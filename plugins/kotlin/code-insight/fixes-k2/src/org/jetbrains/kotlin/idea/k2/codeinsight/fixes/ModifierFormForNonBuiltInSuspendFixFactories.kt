// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddEmptyArgumentListFix
import org.jetbrains.kotlin.idea.quickfix.WrapWithParenthesesFix
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal object ModifierFormForNonBuiltInSuspendFixFactories {

    val addEmptyArgumentListFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ModifierFormForNonBuiltInSuspend ->
            listOfNotNull(
                (diagnostic.psi as? KtCallExpression)?.let { AddEmptyArgumentListFix(it) }
            )
        }

    val wrapWithParenthesesFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ModifierFormForNonBuiltInSuspend ->
            listOfNotNull(
                createWrapWithParenthesesFixIfAvailable(diagnostic.psi)
            )
        }

    val wrapFunWithParenthesesFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ModifierFormForNonBuiltInSuspendFunError ->
            listOfNotNull(
                createWrapWithParenthesesFixIfAvailable(diagnostic.psi)
            )
        }

    private fun createWrapWithParenthesesFixIfAvailable(element: PsiElement): WrapWithParenthesesFix? {
        return element
            .safeAs<KtBinaryExpression>()
            ?.takeIf { it.operationReference.text == "suspend" }
            ?.right
            ?.let { WrapWithParenthesesFix(it) } ?: return null
    }
}
