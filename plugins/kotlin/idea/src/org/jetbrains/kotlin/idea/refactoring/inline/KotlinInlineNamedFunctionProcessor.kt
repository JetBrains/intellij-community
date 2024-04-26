// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.codeInliner.CallableUsageReplacementStrategy
import org.jetbrains.kotlin.idea.codeInliner.CodeToInlineBuilder
import org.jetbrains.kotlin.idea.codeInliner.unwrapSpecialUsageOrNull
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.buildCodeToInline
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class KotlinInlineFunctionProcessor(
    declaration: KtNamedFunction,
    reference: PsiReference?,
    inlineThisOnly: Boolean,
    deleteAfter: Boolean,
    editor: Editor?,
    project: Project,
) : AbstractKotlinInlineNamedDeclarationProcessor<KtNamedFunction>(
    declaration = declaration,
    reference = reference,
    inlineThisOnly = inlineThisOnly,
    deleteAfter = deleteAfter,
    editor = editor,
    project = project,
) {
    override fun createReplacementStrategy(): UsageReplacementStrategy? = createUsageReplacementStrategyForFunction(declaration, editor)
    override fun unwrapSpecialUsage(usage: KtReferenceExpression): KtSimpleNameExpression? {
        return unwrapSpecialUsageOrNull(usage)
    }
}

fun createUsageReplacementStrategyForFunction(
    function: KtNamedFunction,
    editor: Editor?,
    fallbackToSuperCall: Boolean = false,
): UsageReplacementStrategy? {
    val codeToInline = createCodeToInlineForFunction(function, editor, fallbackToSuperCall = fallbackToSuperCall) ?: return null
    return CallableUsageReplacementStrategy(codeToInline, inlineSetter = false)
}

fun createCodeToInlineForFunction(
    function: KtNamedFunction,
    editor: Editor?,
    fallbackToSuperCall: Boolean = false,
): CodeToInline? = buildCodeToInline(
    function,
    function.bodyExpression!!,
    function.hasBlockBody(),
    editor,
    CodeToInlineBuilder(
        originalDeclaration = function,
        fallbackToSuperCall = fallbackToSuperCall,
    ),
)
