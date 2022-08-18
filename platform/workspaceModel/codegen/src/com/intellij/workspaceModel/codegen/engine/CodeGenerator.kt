// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.engine

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass

interface CodeGenerator {
  fun generate(module: CompiledObjModule): List<GeneratedCode>
}

class GeneratedCode(
  val target: ObjClass<*>,
  val builderInterface: String,
  val companionObject: String,
  val topLevelCode: String?,
  val implementationClass: String?
)
