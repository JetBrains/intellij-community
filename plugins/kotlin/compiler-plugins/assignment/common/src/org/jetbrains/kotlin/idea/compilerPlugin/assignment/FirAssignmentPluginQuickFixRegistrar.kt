// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.assignment

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.project.structure.KtCompilerPluginsProvider
import org.jetbrains.kotlin.analysis.project.structure.KtCompilerPluginsProvider.CompilerPluginType
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixesList
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.quickfix.importFix.ImportQuickFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression

internal class FirAssignmentPluginQuickFixRegistrar : KotlinQuickFixRegistrar() {

    private val fixes = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerApplicator(FACTORY)
    }

    override val list: KotlinQuickFixesList = KotlinQuickFixesList.createCombined(fixes)
}

private val FACTORY = diagnosticFixFactory(KtFirDiagnostic.UnresolvedReference::class) { diagnostic ->
    val element = diagnostic.psi
    val project = element.project

    if (element is KtBinaryExpression && element.operationToken == KtTokens.EQ && isAssignmentPluginEnabled(project, element)) {
        ImportQuickFix.getFixes(element.operationReference)
    } else {
        emptyList()
    }
}

private fun isAssignmentPluginEnabled(project: Project, element: PsiElement): Boolean {
    val module = ProjectStructureProvider.getModule(project, element, contextualModule = null) as? KtSourceModule ?: return false
    return project.getService(KtCompilerPluginsProvider::class.java).isPluginOfTypeRegistered(module, CompilerPluginType.ASSIGNMENT)
}