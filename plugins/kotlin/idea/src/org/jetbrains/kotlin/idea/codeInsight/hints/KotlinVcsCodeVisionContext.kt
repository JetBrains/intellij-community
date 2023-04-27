// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.VcsCodeVisionCurlyBracketLanguageContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.siyeh.ipp.psiutils.ErrorUtil
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector
import org.jetbrains.kotlin.idea.util.CommentSaver.Companion.tokenType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.awt.event.MouseEvent

class KotlinVcsCodeVisionContext : VcsCodeVisionCurlyBracketLanguageContext() {
    override fun isAccepted(element: PsiElement): Boolean {
        return when (element) {
            is KtClassOrObject -> isAcceptedClassOrObject(element)
            is KtNamedFunction -> {
                !ErrorUtil.containsError(element) && (element.isTopLevel || isAcceptedClassOrObject(element.containingClassOrObject))
            }
            is KtSecondaryConstructor -> true
            is KtClassInitializer -> true
            is KtProperty -> element.accessors.isNotEmpty()
            else -> false
        }
    }

    private fun isAcceptedClassOrObject(element: KtClassOrObject?): Boolean =
        when (element) {
            is KtClass -> element !is KtEnumEntry
            is KtObjectDeclaration -> !element.isObjectLiteral()
            else -> false
        }

    override fun handleClick(mouseEvent: MouseEvent, editor: Editor, element: PsiElement) {
        val project = element.project
        val location = if (element is KtClassOrObject) KotlinCodeVisionUsagesCollector.CLASS_LOCATION else KotlinCodeVisionUsagesCollector.FUNCTION_LOCATION

        KotlinCodeVisionUsagesCollector.logCodeAuthorClicked(project, location)
    }

    override fun isRBrace(element: PsiElement): Boolean {
        return element.tokenType === KtTokens.RBRACE
    }
}