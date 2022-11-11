// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.substring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression

class ReplaceSubstringWithSubstringAfterInspection : ReplaceSubstringInspection() {
    override fun inspectionText(element: KtDotQualifiedExpression): String =
        KotlinBundle.message("inspection.replace.substring.with.substring.after.display.name")

    override val defaultFixText: String get() = KotlinBundle.message("replace.substring.call.with.substringafter.call")

    override fun applyTo(element: KtDotQualifiedExpression, project: Project, editor: Editor?) {
        element.replaceWith(
            "$0.substringAfter($1)",
            (element.getArgumentExpression(0) as KtDotQualifiedExpression).getArgumentExpression(0)
        )
    }

    override fun isApplicableInner(element: KtDotQualifiedExpression): Boolean {
        val arguments = element.callExpression?.valueArguments ?: return false
        return arguments.size == 1 && isIndexOfCall(arguments[0].getArgumentExpression(), element.receiverExpression)
    }
}

class ReplaceSubstringWithSubstringBeforeInspection : ReplaceSubstringInspection() {
    override fun inspectionText(element: KtDotQualifiedExpression): String =
        KotlinBundle.message("inspection.replace.substring.with.substring.before.display.name")

    override val defaultFixText: String get() = KotlinBundle.message("replace.substring.call.with.substringbefore.call")

    override fun applyTo(element: KtDotQualifiedExpression, project: Project, editor: Editor?) {
        element.replaceWith(
            "$0.substringBefore($1)",
            (element.getArgumentExpression(1) as KtDotQualifiedExpression).getArgumentExpression(0)
        )
    }

    override fun isApplicableInner(element: KtDotQualifiedExpression): Boolean {
        val arguments = element.callExpression?.valueArguments ?: return false

        return arguments.size == 2
                && element.isFirstArgumentZero()
                && isIndexOfCall(arguments[1].getArgumentExpression(), element.receiverExpression)
    }
}

private fun KtDotQualifiedExpression.getArgumentExpression(index: Int): KtExpression {
    return callExpression!!.valueArguments[index].getArgumentExpression()!!
}
