// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory

object ChangeToFunctionInvocationUtils {

    fun applyTo(project: Project, element: KtElement) {
        val psiFactory = KtPsiFactory(project)
        val nextLiteralStringEntry = element.parent.nextSibling as? KtLiteralStringTemplateEntry
        val nextText = nextLiteralStringEntry?.text
        if (nextText != null && nextText.startsWith("(") && nextText.contains(")")) {
            val parentheses = nextText.takeWhile { it != ')' } + ")"
            val newNextText = nextText.removePrefix(parentheses)
            if (newNextText.isNotEmpty()) {
                nextLiteralStringEntry.replace(psiFactory.createLiteralStringTemplateEntry(newNextText))
            } else {
                nextLiteralStringEntry.delete()
            }
            element.replace(psiFactory.createExpression("${element.text}$parentheses"))
        } else {
            element.replace(psiFactory.createExpression("${element.text}()"))
        }
    }
}