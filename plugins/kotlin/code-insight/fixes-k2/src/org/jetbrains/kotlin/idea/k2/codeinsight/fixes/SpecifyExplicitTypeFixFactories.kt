// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.fixes.AbstractKotlinApplicableQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.TypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.getTypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateType
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction

object SpecifyExplicitTypeFixFactories {
    val ambiguousAnonymousTypeInferred =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.AmbiguousAnonymousTypeInferred ->
            createQuickFix(diagnostic.psi)
        }

    val noExplicitReturnTypeInApiMode =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.NoExplicitReturnTypeInApiMode ->
            createQuickFix(diagnostic.psi)
        }

    val noExplicitReturnTypeInApiModeWarning =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.NoExplicitReturnTypeInApiModeWarning ->
            createQuickFix(diagnostic.psi)
        }

    context(KtAnalysisSession)
    private fun createQuickFix(declaration: KtDeclaration) =
        if (declaration is KtCallableDeclaration) listOf(SpecifyExplicitTypeQuickFix(declaration, getTypeInfo(declaration)))
        else emptyList()

    private class SpecifyExplicitTypeQuickFix(
        target: KtCallableDeclaration,
        private val typeInfo: TypeInfo,
    ) : AbstractKotlinApplicableQuickFix<KtCallableDeclaration>(target) {
        override fun getFamilyName(): String = KotlinBundle.message("specify.type.explicitly")

        override fun getActionName(element: KtCallableDeclaration): String = when (element) {
            is KtFunction -> KotlinBundle.message("specify.return.type.explicitly")
            else -> KotlinBundle.message("specify.type.explicitly")
        }

        override fun apply(element: KtCallableDeclaration, project: Project, editor: Editor?, file: KtFile) =
            updateType(element, typeInfo, project, editor)
    }
}