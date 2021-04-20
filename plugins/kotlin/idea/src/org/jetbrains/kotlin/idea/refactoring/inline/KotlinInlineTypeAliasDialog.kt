/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.help.HelpManager
import com.intellij.psi.PsiReference
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.HelpID
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSettings
import org.jetbrains.kotlin.psi.KtTypeAlias
import kotlin.reflect.KMutableProperty1

class KotlinInlineTypeAliasDialog(
    typeAlias: KtTypeAlias,
    reference: PsiReference?,
    editor: Editor?,
) : AbstractKotlinInlineDialog<KtTypeAlias>(typeAlias, reference, editor) {
    init {
        init()
    }

    override fun doHelpAction() = HelpManager.getInstance().invokeHelp(HelpID.INLINE_VARIABLE)
    override val inlineThisOption: KMutableProperty1<KotlinRefactoringSettings, Boolean> get() = KotlinRefactoringSettings::INLINE_TYPE_ALIAS_THIS
    override val inlineKeepOption: KMutableProperty1<KotlinRefactoringSettings, Boolean> get() = KotlinRefactoringSettings::INLINE_TYPE_ALIAS_KEEP
    override fun createProcessor(): BaseRefactoringProcessor = KotlinInlineTypeAliasProcessor(
        declaration = declaration,
        reference = reference,
        inlineThisOnly = isInlineThisOnly,
        deleteAfter = !isInlineThisOnly && !isKeepTheDeclaration,
        editor = editor,
        project = project,
    )
}
