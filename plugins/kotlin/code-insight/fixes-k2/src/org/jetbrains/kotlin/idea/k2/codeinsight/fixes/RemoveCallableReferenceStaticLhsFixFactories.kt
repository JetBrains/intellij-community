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
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments

internal object RemoveCallableReferenceStaticLhsFixFactories {
    val callableReferenceToStaticWarning = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InvalidQualifierInLhsOfCallableReferenceToStaticWarning ->
        createFixes(diagnostic.psi)
    }

    val callableReferenceToStaticError = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InvalidQualifierInLhsOfCallableReferenceToStaticError ->
        createFixes(diagnostic.psi)
    }

    val wrongReceiver = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedReferenceWrongReceiver ->
        createFixes(diagnostic.psi)
    }

    val unsafeCallableReference = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnsafeCallableReference ->
        createFixes(diagnostic.psi)
    }

    private fun createFixes(psi: PsiElement): List<RemoveCallableReferenceStaticLhsFix> {
        val callableReference = psi.getNonStrictParentOfType<KtCallableReferenceExpression>() ?: return listOfNotNull(
            psi.questionMarkTokenBeforeCallableReference()?.let {
                RemoveCallableReferenceStaticLhsFix(it, KotlinBundle.message("text.remove.question"))
            },
            psi.createRemoveTypeArgumentsFix(),
        )
        val lhs = callableReference.lhs ?: return listOfNotNull(
            callableReference.questionMarkTokenBeforeSelf()?.let {
                RemoveCallableReferenceStaticLhsFix(it, KotlinBundle.message("text.remove.question"))
            },
        )
        return listOfNotNull(
            callableReference.questionMarkTokenAfterLhs()?.let {
                RemoveCallableReferenceStaticLhsFix(it, KotlinBundle.message("text.remove.question"))
            },
            lhs.getLhsNullableType()?.takeIf { it.innerType != null }?.let {
                RemoveCallableReferenceStaticLhsFix(it, KotlinBundle.message("text.remove.question"))
            },
            lhs.createRemoveTypeArgumentsFix() ?: psi.createRemoveTypeArgumentsFix(),
        )
    }

    private fun PsiElement.createRemoveTypeArgumentsFix(): RemoveCallableReferenceStaticLhsFix? {
        return findTypeArgumentList()?.createRemoveTypeArgumentsFix()
    }

    private fun KtTypeArgumentList.createRemoveTypeArgumentsFix(): RemoveCallableReferenceStaticLhsFix {
        return RemoveCallableReferenceStaticLhsFix(this, KotlinBundle.message("remove.type.arguments"))
    }

    private fun PsiElement.findTypeArgumentList(): KtTypeArgumentList? {
        return getNonStrictParentOfType<KtTypeArgumentList>()
            ?: findDescendantOfType()
            ?: getPrevSiblingIgnoringWhitespaceAndComments() as? KtTypeArgumentList
            ?: getNextSiblingIgnoringWhitespaceAndComments() as? KtTypeArgumentList
    }

    private fun KtCallableReferenceExpression.questionMarkTokenAfterLhs(): PsiElement? {
        val lhs = lhs ?: return null
        return lhs.takeIf { it.node.elementType == KtTokens.QUEST }
            ?: node.findChildByType(KtTokens.QUEST)?.psi
    }

    private fun KtCallableReferenceExpression.questionMarkTokenBeforeSelf(): PsiElement? {
        return getPrevSiblingIgnoringWhitespaceAndComments()?.takeIf { it.node.elementType == KtTokens.QUEST }
    }

    private fun PsiElement.questionMarkTokenBeforeCallableReference(): PsiElement? {
        val questionMark = getNextSiblingIgnoringWhitespaceAndComments()?.takeIf { it.node.elementType == KtTokens.QUEST } ?: return null
        return questionMark.takeIf { it.getNextSiblingIgnoringWhitespaceAndComments() is KtCallableReferenceExpression }
    }

    private fun PsiElement.getLhsNullableType(): KtNullableType? {
        return when (this) {
            is KtNullableType -> this
            is KtTypeReference -> typeElement as? KtNullableType
            else -> null
        }
    }

    private class RemoveCallableReferenceStaticLhsFix(
        element: PsiElement,
        @Nls private val text: String,
    ) : KotlinPsiUpdateModCommandAction.ElementContextless<PsiElement>(element) {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("remove.question.and.type.arguments.from.invalid.callable.references")

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
