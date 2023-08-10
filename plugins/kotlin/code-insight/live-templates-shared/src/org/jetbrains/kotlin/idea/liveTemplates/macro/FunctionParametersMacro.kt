// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.template.*
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFunction
import java.util.*

class FunctionParametersMacro : KotlinMacro() {
    override fun getName() = "functionParameters"
    override fun getPresentableName() = "functionParameters()"

    override fun calculateResult(params: Array<Expression>, context: ExpressionContext): Result? {
        val project = context.project
        val templateStartOffset = context.templateStartOffset
        val offset = if (templateStartOffset > 0) context.templateStartOffset - 1 else context.templateStartOffset

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val file = PsiDocumentManager.getInstance(project).getPsiFile(context.editor!!.document) ?: return null
        var place = file.findElementAt(offset)
        while (place != null) {
            if (place is KtFunction) {
                val result = ArrayList<Result>()
                for (param in place.valueParameters) {
                    result.add(TextResult(param.name!!))
                }
                return ListResult(result)
            }
            place = place.parent
        }
        return null
    }
}
