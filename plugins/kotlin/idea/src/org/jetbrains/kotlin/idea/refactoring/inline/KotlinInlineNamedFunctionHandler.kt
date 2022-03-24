// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.isAnonymousFunction
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

class KotlinInlineNamedFunctionHandler : AbstractKotlinInlineFunctionHandler<KtNamedFunction>() {
    override fun canInlineKotlinFunction(function: KtFunction): Boolean = function is KtNamedFunction && !function.isAnonymousFunction

    override fun inlineKotlinFunction(project: Project, editor: Editor?, function: KtNamedFunction) {
        val nameReference = editor?.findSimpleNameReference()
        val recursive = function.isRecursive()
        if (recursive && nameReference == null) {
            val message = RefactoringBundle.getCannotRefactorMessage(
                KotlinBundle.message("text.inline.recursive.function.is.supported.only.on.references")
            )

            return showErrorHint(project, editor, message)
        }

        val dialog = KotlinInlineNamedFunctionDialog(
            function,
            nameReference,
            allowToInlineThisOnly = recursive,
            editor = editor,
        )

        if (!isUnitTestMode()) {
            dialog.show()
        } else {
            try {
                dialog.doAction()
            } finally {
                dialog.close(DialogWrapper.OK_EXIT_CODE, true)
            }
        }
    }

    private fun KtNamedFunction.isRecursive(): Boolean {
        val context = analyzeWithContent()
        return bodyExpression?.includesCallOf(context[BindingContext.FUNCTION, this] ?: return false, context) ?: false
    }

    private fun KtExpression.includesCallOf(descriptor: FunctionDescriptor, context: BindingContext): Boolean {
        val refDescriptor = getResolvedCall(context)?.resultingDescriptor
        return descriptor == refDescriptor || anyDescendantOfType<KtExpression> {
            it !== this && descriptor == it.getResolvedCall(context)?.resultingDescriptor
        }
    }
}
