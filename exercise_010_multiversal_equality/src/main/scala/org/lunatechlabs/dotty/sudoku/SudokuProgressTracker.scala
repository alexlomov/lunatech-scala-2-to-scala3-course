package org.lunatechlabs.dotty.sudoku

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

object SudokuProgressTracker {

  enum Command {
    case NewUpdatesInFlight(count: Int)
    case SudokuDetailState(index: Int, state: ReductionSet)
  }
  export Command._

  // My responses
  enum Response {
    case Result(sudoku: Sudoku)
  }
  export Response._

  def apply(rowDetailProcessors: Map[Int, ActorRef[SudokuDetailProcessor.Command]],
            sudokuSolver: ActorRef[Response]): Behavior[Command] =
    Behaviors.setup { context =>
      new SudokuProgressTracker(rowDetailProcessors, context, sudokuSolver).trackProgress(updatesInFlight = 0)
    }
}

class SudokuProgressTracker private (rowDetailProcessors: Map[Int, ActorRef[SudokuDetailProcessor.Command]],
                            context: ActorContext[SudokuProgressTracker.Command],
                            sudokuSolver: ActorRef[SudokuProgressTracker.Response]) {

  import SudokuProgressTracker._

  def trackProgress(updatesInFlight: Int): Behavior[Command] = Behaviors.receiveMessagePartial {
    case NewUpdatesInFlight(updateCount) if updatesInFlight - 1 == 0 =>
      rowDetailProcessors.foreach((_, processor) => processor ! SudokuDetailProcessor.GetSudokuDetailState(context.self))
      collectEndState()
    case NewUpdatesInFlight(updateCount) =>
      trackProgress(updatesInFlight + updateCount)
  }

  def collectEndState(remainingRows: Int = 9, endState: Vector[SudokuDetailState] = Vector.empty[SudokuDetailState]): Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case detail @ SudokuDetailState(index, state) if remainingRows == 1 =>
        sudokuSolver ! Result((detail +: endState).sortBy { case SudokuDetailState(idx, _) => idx }.map { case SudokuDetailState(_, state) => state})
        trackProgress(updatesInFlight = 0)
      case detail @ SudokuDetailState(index, state) =>
        collectEndState(remainingRows = remainingRows - 1, detail +: endState)
    }
}

