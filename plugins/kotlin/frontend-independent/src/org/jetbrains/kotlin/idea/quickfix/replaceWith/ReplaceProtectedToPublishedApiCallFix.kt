// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.replaceWith

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClass

class ReplaceProtectedToPublishedApiCallFix(
    element: KtElement,
    private val originalName: String,
    private val paramNames: List<String>,
    private val newSignature: String,
    private val isProperty: Boolean,
    private val isVar: Boolean,
    private val isPublishedMemberAlreadyExists: Boolean
) : PsiUpdateModCommandAction<KtElement>(element) {
    override fun getFamilyName() = KotlinBundle.message("replace.with.publishedapi.bridge.call")

    private val String.newName: String
        get() = "access\$$this"

    private val String.newNameQuoted: String
        get() = "`$newName`"

    override fun getPresentation(
        context: ActionContext,
        element: KtElement
    ): Presentation? {
        return Presentation.of(
            KotlinBundle.message(
                "replace.with.generated.publishedapi.bridge.call.0",
                originalName.newNameQuoted + if (!isProperty) "(...)" else ""
            )
        )
    }

    override fun invoke(
        context: ActionContext,
        element: KtElement,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(context.project)
        val classOwner = element.containingClass() ?: return

        if (!isPublishedMemberAlreadyExists) {
            val newMember: KtDeclaration =
                if (isProperty) {
                    psiFactory.createProperty(
                        "@kotlin.PublishedApi\n" +
                                "internal " + newSignature +
                                "\n" +
                                "get() = $originalName\n" +
                                if (isVar) "set(value) { $originalName = value }" else ""
                    )

                } else {
                    psiFactory.createFunction(
                        "@kotlin.PublishedApi\n" +
                                "internal " + newSignature +
                                " = $originalName(${paramNames.joinToString(", ")})"
                    ).apply {
                        if (element is KtOperationReferenceExpression) addModifier(KtTokens.INFIX_KEYWORD)
                    }
                }

            ShortenReferencesFacility.getInstance().shorten(classOwner.addDeclaration(newMember))
        }
        if (element is KtOperationReferenceExpression) {
            element.replace(psiFactory.createOperationName(originalName.newNameQuoted))
        } else {
            element.replace(psiFactory.createExpression(originalName.newNameQuoted))
        }
    }
}