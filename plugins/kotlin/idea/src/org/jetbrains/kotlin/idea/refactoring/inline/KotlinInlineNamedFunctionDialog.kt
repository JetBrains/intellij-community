/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.help.HelpManager
import com.intellij.psi.PsiReference
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.HelpID
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSettings
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtNamedFunction
import kotlin.reflect.KMutableProperty1

class KotlinInlineNamedFunctionDialog(
    function: KtNamedFunction,
    reference: PsiReference?,
    editor: Editor?,
    private val allowToInlineThisOnly: Boolean,
) : AbstractKotlinInlineDialog<KtNamedFunction>(function, reference, editor) {
    init {
        init()
    }

    override fun canInlineThisOnly() = allowToInlineThisOnly
    override fun doHelpAction() = HelpManager.getInstance().invokeHelp(
        if (declaration is KtConstructor<*>) HelpID.INLINE_CONSTRUCTOR else HelpID.INLINE_METHOD
    )

    override val inlineThisOption: KMutableProperty1<KotlinRefactoringSettings, Boolean> get() = KotlinRefactoringSettings::INLINE_METHOD_THIS
    override val inlineKeepOption: KMutableProperty1<KotlinRefactoringSettings, Boolean> get() = KotlinRefactoringSettings::INLINE_METHOD_KEEP
    override fun createProcessor(): BaseRefactoringProcessor = KotlinInlineFunctionProcessor(
        declaration = declaration,
        reference = reference,
        inlineThisOnly = isInlineThisOnly || allowToInlineThisOnly,
        deleteAfter = !isInlineThisOnly && !isKeepTheDeclaration && !allowToInlineThisOnly,
        editor = editor,
        project = project,
    )
}
