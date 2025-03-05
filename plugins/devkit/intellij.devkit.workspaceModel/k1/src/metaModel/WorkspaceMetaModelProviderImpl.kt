// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k1.metaModel

import com.intellij.devkit.workspaceModel.metaModel.WorkspaceMetaModelProvider
import com.intellij.openapi.module.Module
import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import org.jetbrains.kotlin.psi.KtClass

internal class WorkspaceMetaModelProviderImpl : WorkspaceMetaModelProvider {

  override fun loadObjModules(
    ktClasses: HashMap<String, KtClass>,
    module: Module,
    processAbstractTypes: Boolean,
    isTestSourceFolder: Boolean,
  ): List<CompiledObjModule> {
    val packages = ktClasses.values.mapTo(LinkedHashSet()) { it.containingKtFile.packageFqName.asString() }

    val metaModelProvider = WorkspaceMetaModelBuilder(
      processAbstractTypes = processAbstractTypes,
      module.project
    )
    return packages.filter { it != "" }.map { metaModelProvider.getObjModule(it, module, isTestSourceFolder) }
  }
}
