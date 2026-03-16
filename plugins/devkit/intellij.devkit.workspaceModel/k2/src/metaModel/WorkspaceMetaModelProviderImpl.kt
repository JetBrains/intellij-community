// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k2.metaModel

import com.intellij.devkit.workspaceModel.metaModel.MetaModelBuilderException
import com.intellij.devkit.workspaceModel.metaModel.MetaProblem
import com.intellij.devkit.workspaceModel.metaModel.WorkspaceMetaModelProvider
import com.intellij.openapi.module.Module
import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForTest
import org.jetbrains.kotlin.psi.KtClassOrObject

internal class WorkspaceMetaModelProviderImpl : WorkspaceMetaModelProvider {

  @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
  override fun loadObjModules(
    ktClasses: HashMap<String, KtClassOrObject>,
    module: Module,
    processAbstractTypes: Boolean,
    isTestSourceFolder: Boolean,
  ): Pair<List<CompiledObjModule>, List<MetaProblem>> {
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
      return (emptyList<CompiledObjModule>() to emptyList())
    }
    val problems: MutableList<MetaProblem> = mutableListOf()
    val compiledObjModules = allowAnalysisOnEdt {
      allowAnalysisFromWriteAction {
        packages
          .filter { it != "" }
          .mapNotNull { packageName ->
            metaModelProvider.getObjModuleOrProblem(packageName, kaModule, problems)
          }
      }
    }
    return compiledObjModules to problems
  }

  private fun WorkspaceMetaModelBuilder.getObjModuleOrProblem(
    packageName: String,
    kaModule: KaSourceModule,
    problems: MutableList<MetaProblem>
  ): CompiledObjModule? {
    try {
      return getObjModule(packageName, kaModule)
    }
    catch (e: MetaModelBuilderException) {
      problems.add(MetaProblem(e.message ?: "", e.psiToHighlight))
      return null
    }
  }
}
