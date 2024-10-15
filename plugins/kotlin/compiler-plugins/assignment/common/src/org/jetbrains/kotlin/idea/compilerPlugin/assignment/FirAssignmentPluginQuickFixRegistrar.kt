// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.assignment

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinCompilerPluginsProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinCompilerPluginsProvider.CompilerPluginType
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFixProvider
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModuleOfTypeSafe

internal class FirAssignmentPluginQuickFixRegistrar : KotlinQuickFixRegistrar() {

    private val fixes = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerFactory(FACTORY)
    }

    override val list: KotlinQuickFixesList = KotlinQuickFixesList.createCombined(fixes)
}

private val FACTORY = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UnresolvedReference ->
    val element = diagnostic.psi
    val project = element.project

    if (element is KtBinaryExpression && element.operationToken == KtTokens.EQ && isAssignmentPluginEnabled(project, element)) {
        ImportQuickFixProvider.getFixes(element.operationReference)
    } else {
        emptyList()
    }
}

private fun isAssignmentPluginEnabled(project: Project, element: PsiElement): Boolean {
    val module = element.getKaModuleOfTypeSafe<KaSourceModule>(project, useSiteModule = null) ?: return false
    val compilerPluginsProvider = KotlinCompilerPluginsProvider.getInstance(project) ?: return false
    return compilerPluginsProvider.isPluginOfTypeRegistered(module, CompilerPluginType.ASSIGNMENT)
}
