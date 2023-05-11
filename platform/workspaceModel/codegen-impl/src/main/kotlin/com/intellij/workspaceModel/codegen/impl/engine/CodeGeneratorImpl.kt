// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.engine

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.engine.CodeGenerator
import com.intellij.workspaceModel.codegen.engine.GeneratedCode
import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.engine.GenerationResult
import com.intellij.workspaceModel.codegen.impl.writer.*

class CodeGeneratorImpl : CodeGenerator {
  override fun generate(module: CompiledObjModule): GenerationResult {
    val problems = ArrayList<GenerationProblem>()
    val reporter = ProblemReporter { problems.add(it) }

    checkExtensionFields(module, reporter)
    val objClassToBuilderInterface = module.types.associateWith {
      val builderInterface = it.generateBuilderCode(reporter)
      builderInterface
    }
    // If there is at least one error, report them and stop any further calculations and
    if (problems.any { it.level == GenerationProblem.Level.ERROR }) {
      return GenerationResult(emptyList(), problems)
    }

    val code = objClassToBuilderInterface.map { (objClass, builderInterface) ->
      GeneratedCode(
        target = objClass,
        builderInterface = builderInterface,
        companionObject = objClass.generateCompanionObject(),
        topLevelCode = objClass.generateExtensionCode(),
        implementationClass = objClass.implWsCode()
      )
    }
    return GenerationResult(code, problems)
  }
}

fun interface ProblemReporter {
  fun reportProblem(problem: GenerationProblem)
}