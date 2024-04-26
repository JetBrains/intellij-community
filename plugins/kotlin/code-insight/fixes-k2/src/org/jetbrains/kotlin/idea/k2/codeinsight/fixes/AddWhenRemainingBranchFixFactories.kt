// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.fixes.AbstractKotlinApplicableQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddRemainingWhenBranchesUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddRemainingWhenBranchesUtils.addRemainingWhenBranches
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtWhenExpression

object AddWhenRemainingBranchFixFactories {

    val noElseInWhen = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.NoElseInWhen ->
        val whenExpression = diagnostic.psi
        val subjectExpression = whenExpression.subjectExpression
            ?: return@IntentionBased emptyList()

        buildList {
            val missingCases = diagnostic.missingWhenCases.takeIf {
                it.isNotEmpty() && it.singleOrNull() != WhenMissingCase.Unknown
            } ?: return@buildList
            add(
                AddRemainingWhenBranchesQuickFix(
                    whenExpression,
                    AddRemainingWhenBranchesUtils.Context(missingCases, null),
                )
            )

            val baseClassSymbol = subjectExpression.getKtType()?.expandedClassSymbol ?: return@buildList
            val enumToStarImport = baseClassSymbol.classIdIfNonLocal
            if (baseClassSymbol.classKind == KtClassKind.ENUM_CLASS && enumToStarImport != null) {
                add(
                    AddRemainingWhenBranchesQuickFix(
                        whenExpression,
                        AddRemainingWhenBranchesUtils.Context(missingCases, enumToStarImport),
                    )
                )
            }
        }
    }

    private class AddRemainingWhenBranchesQuickFix(
        target: KtWhenExpression,
        @SafeFieldForPreview private val context: AddRemainingWhenBranchesUtils.Context,
    ) : AbstractKotlinApplicableQuickFix<KtWhenExpression>(target) {
        override fun getFamilyName(): String = AddRemainingWhenBranchesUtils.familyAndActionName(context.enumToStarImport != null)
        override fun apply(element: KtWhenExpression, project: Project, editor: Editor?, file: KtFile) =
            addRemainingWhenBranches(element, context)
    }
}