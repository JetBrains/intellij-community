// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddRemainingWhenBranchesUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddRemainingWhenBranchesUtils.addRemainingWhenBranches
import org.jetbrains.kotlin.psi.KtWhenExpression

object AddWhenRemainingBranchFixFactories {

    val noElseInWhen = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoElseInWhen ->
        val whenExpression = diagnostic.psi
        val subjectExpression = whenExpression.subjectExpression
            ?: return@ModCommandBased emptyList()

        buildList {
            val missingCases = diagnostic.missingWhenCases.takeIf {
                it.isNotEmpty() && it.singleOrNull() != WhenMissingCase.Unknown
            } ?: return@buildList
            add(
                AddRemainingWhenBranchesQuickFix(
                    whenExpression,
                    AddRemainingWhenBranchesUtils.ElementContext(missingCases, null),
                )
            )

          val baseClassSymbol = subjectExpression.expressionType?.expandedSymbol ?: return@buildList
            val enumToStarImport = baseClassSymbol.classId
            if (baseClassSymbol.classKind == KaClassKind.ENUM_CLASS && enumToStarImport != null) {
                add(
                    AddRemainingWhenBranchesQuickFix(
                        whenExpression,
                        AddRemainingWhenBranchesUtils.ElementContext(missingCases, enumToStarImport),
                    )
                )
            }
        }
    }

    private class AddRemainingWhenBranchesQuickFix(
        element: KtWhenExpression,
        private val elementContext: AddRemainingWhenBranchesUtils.ElementContext,
    ) : PsiUpdateModCommandAction<KtWhenExpression>(element) {
        override fun getFamilyName(): String = AddRemainingWhenBranchesUtils.familyAndActionName(elementContext.enumToStarImport != null)
        override fun invoke(
            context: ActionContext,
            element: KtWhenExpression,
            updater: ModPsiUpdater,
        ) = addRemainingWhenBranches(element, elementContext)
    }
}