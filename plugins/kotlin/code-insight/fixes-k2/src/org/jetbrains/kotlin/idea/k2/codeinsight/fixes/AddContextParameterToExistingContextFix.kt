// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument

internal class AddContextParameterToExistingContextFix(
    surroundingCall: KtCallExpression,
    private val candidateName: String?,
    private val parameterTypeText: String,
    private val parameterTypeFqNameText: String
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtCallExpression>(surroundingCall) {

    override fun invoke(context: ActionContext, element: KtCallExpression, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(context.project)
        val argList = element.valueArgumentList ?: return
        val rightParen = argList.rightParenthesis ?: return
        if (candidateName != null && argList.arguments.any {
                (it.getArgumentExpression() as? KtNameReferenceExpression)?.getReferencedName() == candidateName
            }) return

        val hasTrailingComma = PsiTreeUtil.skipWhitespacesAndCommentsBackward(rightParen)
            ?.node?.elementType == KtTokens.COMMA
        if (argList.arguments.isNotEmpty() && !hasTrailingComma) {
            argList.addBefore(psiFactory.createComma(), rightParen)
        }
        val newElement =
            if (candidateName != null) {
                psiFactory.createArgument(candidateName)
            } else {
                val typeReference = psiFactory.createType(parameterTypeFqNameText)
                val userType = shortenReferences(typeReference) as? KtUserType
                psiFactory.createArgument(
                    psiFactory.createExpression("TODO(\"Provide $parameterTypeText\")${userType?.let { " as ${it.text}" } ?: ""}")
                )
            }
        val insertedElement = argList.addBefore(newElement, rightParen) as? KtValueArgument ?: return
        if (candidateName == null) {
            val insertedExpression = insertedElement.getArgumentExpression() ?: return
            updater.moveCaretTo(insertedExpression)
            updater.templateBuilder().field(insertedExpression, insertedExpression.text)
        }
    }

    override fun getActionPresentation(context: ActionContext, element: KtCallExpression): Presentation =
        Presentation.of(
            if (candidateName != null) {
                KotlinBundle.message(
                    "fix.add.argument.to.existing.context.with.name.and.type",
                    candidateName,
                    parameterTypeText,
                )
            } else {
                KotlinBundle.message(
                    "fix.add.todo.argument.to.existing.context.with.name.and.type",
                    parameterTypeText,
                )
            }
        )

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.add.argument.to.existing.context")
}