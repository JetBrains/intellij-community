// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.engine.impl

import com.intellij.workspaceModel.codegen.*
import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.engine.CodeGenerator
import com.intellij.workspaceModel.codegen.engine.GeneratedCode

class CodeGeneratorImpl : CodeGenerator {
  override fun generate(module: CompiledObjModule): List<GeneratedCode> {
    return module.types.map { objClass ->
      GeneratedCode(
        target = objClass,
        builderInterface = objClass.generateBuilderCode(),
        companionObject = objClass.generateCompanionObject(),
        topLevelCode = objClass.generateExtensionCode(),
        implementationClass = objClass.implWsCode()
      )
    }
  }
}