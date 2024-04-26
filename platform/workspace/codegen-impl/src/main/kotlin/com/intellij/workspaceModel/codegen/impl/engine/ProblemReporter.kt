package com.intellij.workspaceModel.codegen.impl.engine

import com.intellij.workspaceModel.codegen.engine.GenerationProblem

interface ProblemReporter {
  val problems: List<GenerationProblem>

  fun reportProblem(problem: GenerationProblem)

  fun hasErrors(): Boolean = problems.any { it.level == GenerationProblem.Level.ERROR }
}

internal class ProblemReporterImpl(
  private val mutableProblems: MutableList<GenerationProblem> = ArrayList()
): ProblemReporter {

  override fun reportProblem(problem: GenerationProblem) {
    mutableProblems.add(problem)
  }

  override val problems: List<GenerationProblem>
    get() = mutableProblems
}