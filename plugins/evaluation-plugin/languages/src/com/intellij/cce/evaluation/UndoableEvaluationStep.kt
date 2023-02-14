package com.intellij.cce.evaluation

interface UndoableEvaluationStep : EvaluationStep {
  fun undoStep(): UndoStep

  interface UndoStep : EvaluationStep
}