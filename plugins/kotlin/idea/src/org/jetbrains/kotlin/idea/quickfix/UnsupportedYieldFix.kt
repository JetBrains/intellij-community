// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.MESSAGE_FOR_YIELD_BEFORE_LAMBDA

class UnsupportedYieldFix(psiElement: PsiElement) : KotlinQuickFixAction<PsiElement>(psiElement), CleanupFix {
    override fun getFamilyName(): String = KotlinBundle.message("migrate.unsupported.yield.syntax")
    override fun getText(): String = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val psiElement = element ?: return
        val psiFactory = KtPsiFactory(project)

        if (psiElement is KtCallExpression) {
            val ktExpression = (psiElement as KtCallElement).calleeExpression ?: return

            // Add after "yield" reference in call
            psiElement.addAfter(psiFactory.createCallArguments("()"), ktExpression)
        }

        if (psiElement.node.elementType == KtTokens.IDENTIFIER) {
            psiElement.replace(psiFactory.createIdentifier("`yield`"))
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            if (diagnostic.psiElement.text != "yield") return null

            val message = Errors.YIELD_IS_RESERVED.cast(diagnostic).a
            if (message == MESSAGE_FOR_YIELD_BEFORE_LAMBDA) {
                // Identifier -> Expression -> Call (normal call) or Identifier -> Operation Reference -> Binary Expression (for infix usage)
                val grand = (diagnostic.psiElement.parent as? KtNameReferenceExpression)?.parent
                if (grand is KtBinaryExpression || grand is KtCallExpression) {
                    return UnsupportedYieldFix(grand)
                }
            } else {
                return UnsupportedYieldFix(diagnostic.psiElement)
            }

            return null
        }
    }
}