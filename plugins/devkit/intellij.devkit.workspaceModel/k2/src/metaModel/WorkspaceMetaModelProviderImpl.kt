// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k2.metaModel

import com.intellij.devkit.workspaceModel.metaModel.WorkspaceMetaModelProvider
import com.intellij.openapi.module.Module
import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForTest
import org.jetbrains.kotlin.psi.KtClass

internal class WorkspaceMetaModelProviderImpl : WorkspaceMetaModelProvider {

  @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
  override fun loadObjModules(
    ktClasses: HashMap<String, KtClass>,
    module: Module,
    processAbstractTypes: Boolean,
    isTestSourceFolder: Boolean,
  ): List<CompiledObjModule> {
    val packages = ktClasses.values.mapTo(LinkedHashSet()) { it.containingKtFile.packageFqName.asString() }

    val metaModelProvider = WorkspaceMetaModelBuilder(
      processAbstractTypes,
      module.project
    )
    val kaModule = if (!isTestSourceFolder) {
      module.toKaSourceModuleForProduction()
    }
    else {
      module.toKaSourceModuleForTest()
    }
    if (kaModule == null) {
      return emptyList()
    }
    val compiledObjModule = allowAnalysisOnEdt {
      allowAnalysisFromWriteAction {
        packages
          .filter { it != "" }
          .mapNotNull { packageName ->
            metaModelProvider.getObjModule(packageName, kaModule)
          }
      }
    }
    return compiledObjModule
  }
}
