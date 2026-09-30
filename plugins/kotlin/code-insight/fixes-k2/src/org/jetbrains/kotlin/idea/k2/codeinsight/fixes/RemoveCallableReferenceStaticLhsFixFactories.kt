// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal object RemoveCallableReferenceStaticLhsFixFactories {
    val warning = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InvalidQualifierInLhsOfCallableReferenceToStaticWarning ->
        createFixes(diagnostic.psi)
    }

    val error = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InvalidQualifierInLhsOfCallableReferenceToStaticError ->
        createFixes(diagnostic.psi)
    }

    val wrongReceiver = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedReferenceWrongReceiver ->
        createFixes(diagnostic.psi)
    }

    private fun createFixes(psi: PsiElement): List<RemoveCallableReferenceStaticLhsFix> {
        val callableReference = psi.getNonStrictParentOfType<KtCallableReferenceExpression>() ?: return emptyList()
        val lhs = callableReference.lhs ?: return emptyList()
        return listOfNotNull(
            lhs.takeIf { callableReference.hasQuestionMarks && it.node.elementType == KtTokens.QUEST }?.let {
                RemoveCallableReferenceStaticLhsFix(it, KotlinBundle.message("text.remove.question"))
            },
            lhs.findSelfOrDescendant<KtNullableType>()?.takeIf { it.innerType != null }?.let {
                RemoveCallableReferenceStaticLhsFix(it, KotlinBundle.message("text.remove.question"))
            },
            lhs.findSelfOrDescendant<KtTypeArgumentList>()?.let {
                RemoveCallableReferenceStaticLhsFix(it, KotlinBundle.message("remove.type.arguments"))
            },
        )
    }

    private inline fun <reified T : PsiElement> PsiElement.findSelfOrDescendant(): T? {
        return this as? T ?: findDescendantOfType()
    }

    private class RemoveCallableReferenceStaticLhsFix(
        element: PsiElement,
        @Nls private val text: String,
    ) : KotlinPsiUpdateModCommandAction.ElementContextless<PsiElement>(element) {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("remove.element")

        override fun getActionPresentation(context: ActionContext, element: PsiElement): Presentation =
            Presentation.of(text)

        override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
            when (element) {
                is KtNullableType -> element.replace(element.innerType ?: return)
                is KtTypeArgumentList -> element.delete()
                else -> element.delete()
            }
        }
    }
}
