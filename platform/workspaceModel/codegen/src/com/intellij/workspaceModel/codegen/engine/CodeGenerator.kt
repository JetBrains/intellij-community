// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.engine

import com.intellij.workspaceModel.codegen.deft.meta.*

interface CodeGenerator {
  fun generate(module: CompiledObjModule): GenerationResult
}

class GenerationResult(val generatedCode: List<GeneratedCode>, val problems: List<GenerationProblem>)

class GenerationProblem(
  val message: String,
  val level: Level,
  val location: ProblemLocation
) {
  enum class Level { ERROR, WARNING }
}

sealed interface ProblemLocation {
  val objModule: ObjModule
  
  class Class(val objClass: ObjClass<*>) : ProblemLocation {
    override val objModule: ObjModule
      get() = objClass.module

    override fun toString(): String = objClass.name
  }
  class Property(val property: ObjProperty<*, *>) : ProblemLocation {
    override val objModule: ObjModule
      get() = when (property) {
        is ExtProperty<*, *> -> property.module
        else -> property.receiver.module
      }

    override fun toString(): String = property.toString()
  }
}

class GeneratedCode(
  val target: ObjClass<*>,
  val builderInterface: String,
  val companionObject: String,
  val topLevelCode: String?,
  val implementationClass: String?
)
