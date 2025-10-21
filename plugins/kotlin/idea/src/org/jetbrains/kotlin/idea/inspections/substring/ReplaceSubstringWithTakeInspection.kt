// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.substring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isSimplifiableTo
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class ReplaceSubstringWithTakeInspection : ReplaceSubstringInspection() {
    override fun inspectionText(element: KtDotQualifiedExpression): String =
        KotlinBundle.message("inspection.replace.substring.with.take.display.name")

    override val defaultFixText: String get() = KotlinBundle.message("replace.substring.call.with.take.call")

    override fun applyTo(element: KtDotQualifiedExpression, project: Project, editor: Editor?) {
        val argument = element.callExpression?.valueArguments?.elementAtOrNull(1)?.getArgumentExpression() ?: return
        element.replaceWith("$0.take($1)", argument)
    }

    override fun isApplicableInner(element: KtDotQualifiedExpression): Boolean {
        val arguments = element.callExpression?.valueArguments ?: return false
        if (arguments.size != 2 || !element.isFirstArgumentZero()) return false
        
        // Don't trigger if ReplaceSubstringWithDropLastInspection would be triggered
        val secondArgument = arguments[1].getArgumentExpression() as? KtBinaryExpression ?: return true
        if (secondArgument.operationReference.getReferencedNameElementType() != KtTokens.MINUS) return true
        return !isLengthAccess(secondArgument.left, element.receiverExpression)
    }

    private fun isLengthAccess(expression: KtExpression?, expectedReceiver: KtExpression): Boolean =
        expression is KtDotQualifiedExpression
                && expression.selectorExpression.let { it is KtNameReferenceExpression && it.getReferencedName() == "length" }
                && expression.receiverExpression.isSimplifiableTo(expectedReceiver)
}
