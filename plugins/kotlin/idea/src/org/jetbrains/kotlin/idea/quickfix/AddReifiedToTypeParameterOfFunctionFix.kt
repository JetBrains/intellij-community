// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class AddReifiedToTypeParameterOfFunctionFix(
    typeParameter: KtTypeParameter,
    function: KtNamedFunction
) : AddModifierFixFE10(typeParameter, KtTokens.REIFIED_KEYWORD) {

    private val inlineFix = AddInlineToFunctionWithReifiedFix(function)
    private val elementName = RemoveModifierFixBase.getElementName(function)

    override fun getText() =
        element?.let { KotlinBundle.message("fix.make.type.parameter.reified", RemoveModifierFixBase.getElementName(it), elementName) } ?: ""

    override fun invokeImpl(project: Project, editor: Editor?, file: PsiFile) {
        super.invokeImpl(project, editor, file)
        inlineFix.invoke(project, editor, file)
    }

    companion object Factory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val element = Errors.TYPE_PARAMETER_AS_REIFIED.cast(diagnostic)
            val function = element.psiElement.getStrictParentOfType<KtNamedFunction>()
            val parameter = function?.typeParameterList?.parameters?.getOrNull(element.a.index) ?: return null
            return AddReifiedToTypeParameterOfFunctionFix(parameter, function)
        }
    }
}