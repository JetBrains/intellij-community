// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

internal class KotlinPostfixTemplateProvider : PostfixTemplateProvider {
    private val templateSet: Set<PostfixTemplate> by lazy {
        setOf(
            KotlinParenthesizedPostfixTemplate(this),
            KotlinAssertPostfixTemplate(this),
            KotlinSystemOutPostfixTemplate(this),
            KotlinWithPostfixTemplate(this),
            KotlinWhilePostfixTemplate(this),
            KotlinReturnPostfixTemplate(this),
            KotlinSpreadPostfixTemplate(this),
            KotlinForPostfixTemplate(this),
            KotlinIterPostfixTemplate(this),
            KotlinWrapIntoListPostfixTemplate(this),
            KotlinWrapIntoSetPostfixTemplate(this),
            KotlinWrapIntoArrayPostfixTemplate(this),
            KotlinWrapIntoSequencePostfixTemplate(this),
            KotlinIfPostfixTemplate(this),
            KotlinUnlessPostfixTemplate(this),
            KotlinNotNullPostfixTemplate(this),
            KotlinNnPostfixTemplate(this),
            KotlinNullPostfixTemplate(this),
            KotlinTryPostfixTemplate(this),
            KotlinWhenPostfixTemplate(this),
            KotlinNotPostfixTemplate(this)
        )
    }

    override fun getTemplates() = templateSet

    override fun isTerminalSymbol(currentChar: Char) = currentChar == '.' || currentChar == '!'

    override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int) = copyFile

    override fun preExpand(file: PsiFile, editor: Editor) {}
    override fun afterExpand(file: PsiFile, editor: Editor) {}
}