// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier

internal class ConvertPropertyGetterToInitializerIntention :
    KotlinApplicableModCommandAction.Simple<KtPropertyAccessor>(KtPropertyAccessor::class) {

    override fun stopSearchAt(
        element: PsiElement,
        context: ActionContext,
    ): Boolean = false

    override fun getFamilyName() =
        KotlinBundle.message("convert.property.getter.to.initializer")

    override fun isApplicableByPsi(element: KtPropertyAccessor): Boolean {
        if (!element.isGetter || element.singleExpression() == null || element.property.contextReceiverList != null) return false

        val property = element.parent as? KtProperty ?: return false
        return !property.hasInitializer() &&
                property.receiverTypeReference == null &&
                property.containingClass()?.isInterface() != true &&
                element.modifierList?.hasModifier(KtTokens.EXPECT_KEYWORD) != true &&
                element.containingClass()?.hasExpectModifier() != true
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtPropertyAccessor,
      elementContext: Unit,
      updater: ModPsiUpdater,
    ) {
        val property = element.parent as? KtProperty ?: return
        val commentSaver = CommentSaver(property)
        property.initializer = element.singleExpression()
        property.deleteChildRange(property.initializer?.nextSibling, element)
        updater.moveCaretTo(property.endOffset)
        commentSaver.restore(property)
    }
}

private fun KtPropertyAccessor.singleExpression(): KtExpression? = when (val bodyExpression = bodyExpression) {
    is KtBlockExpression -> (bodyExpression.statements.singleOrNull() as? KtReturnExpression)?.returnedExpression
    else -> bodyExpression
}
