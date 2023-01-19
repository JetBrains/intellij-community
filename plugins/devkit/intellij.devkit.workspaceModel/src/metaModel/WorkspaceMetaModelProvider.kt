// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import org.jetbrains.kotlin.descriptors.SourceElement

interface WorkspaceMetaModelProvider {
  companion object {
    fun getInstance(project: Project): WorkspaceMetaModelProvider = project.service()
  }

  fun getObjModule(packageName: String, module: Module): CompiledObjModule
}

class IncorrectObjInterfaceException(errorMessage: String): RuntimeException(errorMessage)

interface ObjMetaElementWithSource {
  val sourceElement: SourceElement
}