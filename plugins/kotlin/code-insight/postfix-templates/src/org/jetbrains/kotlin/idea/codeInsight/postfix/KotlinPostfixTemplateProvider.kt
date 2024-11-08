// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

// K2 PostfixTemplateProvider
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
            KotlinItorPostfixTemplate(this),
            KotlinForReversedPostfixTemplate(this),
            KotlinForWithIndexPostfixTemplate(this),
            KotlinForLoopNumbersPostfixTemplate(this),
            KotlinForLoopReverseNumbersPostfixTemplate(this),
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
            KotlinNotPostfixTemplate(this),
            KotlinValPostfixTemplate(this),
            KotlinVarPostfixTemplate(this),
        )
    }

    override fun getTemplates(): Set<PostfixTemplate> {
        return templateSet
    }

    override fun isTerminalSymbol(currentChar: Char): Boolean = currentChar == '.' || currentChar == '!'

    override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int): PsiFile = copyFile

    override fun preExpand(file: PsiFile, editor: Editor) {}
    override fun afterExpand(file: PsiFile, editor: Editor) {}
}