package com.intellij.workspaceModel.codegen.impl.dsl

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjProperty
import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.engine.ProblemLocation

interface GeneratorContext {
  val testModeEnabled: Boolean
  val explicitApiModifier: String
  val problems: List<GenerationProblem>

  fun reportProblem(problem: GenerationProblem)

  fun reportPropertyError(message: String, objProperty: ObjProperty<*, *>) {
    reportProblem(GenerationProblem(message, GenerationProblem.Level.ERROR, ProblemLocation.Property(objProperty)))
  }

  fun reportClassError(message: String, objClass: ObjClass<*>) {
    reportProblem(GenerationProblem(message, GenerationProblem.Level.ERROR, ProblemLocation.Class(objClass)))
  }

  fun hasErrors(): Boolean = problems.any { it.level == GenerationProblem.Level.ERROR }
}
