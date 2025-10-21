// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.fake

import com.intellij.codeInsight.FileModificationService
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.isReferenceToImplicitLambdaParameter
import org.jetbrains.kotlin.idea.refactoring.rename.handlers.RenameKotlinImplicitLambdaParameter
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class ReplaceItWithExplicitFunctionLiteralParamIntention : SelfTargetingOffsetIndependentIntention<KtNameReferenceExpression>(
  KtNameReferenceExpression::class.java, KotlinBundle.messagePointer("replace.it.with.explicit.parameter")
) {
    override fun isApplicableTo(element: KtNameReferenceExpression): Boolean = element.isReferenceToImplicitLambdaParameter()

    override fun startInWriteAction(): Boolean = false

    override fun applyTo(element: KtNameReferenceExpression, editor: Editor?) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return
        if (editor == null) throw IllegalArgumentException("This intention requires an editor")

        editor.caretModel.moveToOffset(element.textOffset)
        val ktFile = element.containingKtFile

        val dataContext = DataContext { id ->
          when {
            CommonDataKeys.PSI_ELEMENT.`is`(id) -> element
            else -> null
          }
        }
        RenameKotlinImplicitLambdaParameter().invoke(ktFile.project, editor, ktFile, dataContext)
    }
}