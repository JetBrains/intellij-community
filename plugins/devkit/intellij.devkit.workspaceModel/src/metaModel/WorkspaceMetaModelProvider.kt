// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel

import com.intellij.openapi.module.Module
import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import org.jetbrains.kotlin.psi.KtClass

interface WorkspaceMetaModelProvider {
  fun loadObjModules(
    ktClasses: HashMap<String, KtClass>,
    module: Module,
    processAbstractTypes: Boolean,
    isTestSourceFolder: Boolean,
  ): List<CompiledObjModule>
}
