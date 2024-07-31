// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ConvertStringToCharLiteralUtils
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal object ConvertStringToCharLiteralFixFactory {

    val argumentTypeMismatchFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        getFixes(diagnostic.psi, diagnostic.expectedType)
    }

    val assignmentTypeMismatchFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        getFixes(diagnostic.psi, diagnostic.expectedType)
    }

    val equalityNotApplicableFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.EqualityNotApplicable ->
        val element = diagnostic.psi
        getFixes(element.left, diagnostic.rightType).takeUnless { it.isEmpty() } ?: getFixes(element.right, diagnostic.leftType)
    }

    val incompatibleTypesFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.IncompatibleTypes ->
        getFixes(diagnostic.psi, diagnostic.typeA)
    }

    val initializerTypeMismatchFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        getFixes((diagnostic.psi as? KtProperty)?.initializer, diagnostic.expectedType)
    }

    val returnTypeMismatchFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        getFixes(diagnostic.psi, diagnostic.expectedType)
    }

    context(KaSession)
    private fun getFixes(element: PsiElement?, expectedType: KaType): List<ConvertStringToCharLiteralFix> {
        if (element !is KtStringTemplateExpression) return emptyList()
        if (!expectedType.isCharType) return emptyList()

        val charLiteral = ConvertStringToCharLiteralUtils.prepareCharLiteral(element) ?: return emptyList()
        if (charLiteral.evaluate() == null) return emptyList()

        return listOf(
            ConvertStringToCharLiteralFix(element, charLiteral)
        )
    }

    private class ConvertStringToCharLiteralFix(
        element: KtStringTemplateExpression,
        private val charLiteral: KtExpression, // No need for `SmartPsiElementPointer` since the expression is created by `KtPsiFactory`
    ) : PsiUpdateModCommandAction<KtStringTemplateExpression>(element) {

        override fun getFamilyName(): String = KotlinBundle.message("convert.string.to.character.literal")

        override fun invoke(
            actionContext: ActionContext,
            element: KtStringTemplateExpression,
            updater: ModPsiUpdater,
        ) {
            element.replace(charLiteral)
        }
    }
}
