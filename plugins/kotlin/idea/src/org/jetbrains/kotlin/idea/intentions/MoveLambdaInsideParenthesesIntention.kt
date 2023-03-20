// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.core.moveInsideParentheses
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.psiUtil.containsInside

class MoveLambdaInsideParenthesesIntention : SelfTargetingIntention<KtLambdaArgument>(
    KtLambdaArgument::class.java, KotlinBundle.lazyMessage("move.lambda.argument.into.parentheses")
), LowPriorityAction {
    override fun isApplicableTo(element: KtLambdaArgument, caretOffset: Int): Boolean {
        val body = element.getLambdaExpression()?.bodyExpression ?: return true
        return !body.textRange.containsInside(caretOffset)
    }

    override fun applyTo(element: KtLambdaArgument, editor: Editor?) {
        element.moveInsideParentheses(element.safeAnalyzeNonSourceRootCode())
    }
}

