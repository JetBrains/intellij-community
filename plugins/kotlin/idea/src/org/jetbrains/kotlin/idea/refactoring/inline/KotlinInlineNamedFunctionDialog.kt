// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiReference
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinInlineNamedFunctionDialog(
    function: KtNamedFunction,
    reference: PsiReference?,
    editor: Editor?,
    allowToInlineThisOnly: Boolean,
) : AbstractKotlinInlineNamedFunctionDialog(function, reference, editor, allowToInlineThisOnly) {
    init {
        init()
    }

    override fun createProcessor(): BaseRefactoringProcessor = KotlinInlineFunctionProcessor(
        declaration = declaration,
        reference = reference,
        inlineThisOnly = isInlineThisOnly || allowToInlineThisOnly,
        deleteAfter = !isInlineThisOnly && !isKeepTheDeclaration && !allowToInlineThisOnly,
        editor = editor,
        project = project,
    )
}
