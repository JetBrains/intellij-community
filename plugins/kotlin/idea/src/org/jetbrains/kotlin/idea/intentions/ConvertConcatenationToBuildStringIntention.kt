// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.base.psi.isInsideAnnotationEntryArgumentList
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.convertConcatenationToBuildStringCall
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ConvertConcatenationToBuildStringIntention : SelfTargetingIntention<KtBinaryExpression>(
    KtBinaryExpression::class.java,
    KotlinBundle.messagePointer("convert.concatenation.to.build.string")
) {

    override fun isApplicableTo(element: KtBinaryExpression, caretOffset: Int): Boolean {
        return element.isConcatenation() && !element.parent.isConcatenation() && !element.isInsideAnnotationEntryArgumentList()
    }

    override fun applyTo(element: KtBinaryExpression, editor: Editor?) {
        val buildStringCall = convertConcatenationToBuildStringCall(element)
        ShortenReferences.DEFAULT.process(buildStringCall)
    }

    private fun PsiElement.isConcatenation(): Boolean {
        if (this !is KtBinaryExpression) return false
        if (operationToken != KtTokens.PLUS) return false
        val type = getType(safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)) ?: return false
        return KotlinBuiltIns.isString(type)
    }
}
