// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.conventionNameCalls

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.OperatorToFunctionConverter
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.intentions.calleeName
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ReplaceInvokeIntention : SelfTargetingRangeIntention<KtDotQualifiedExpression>(
    KtDotQualifiedExpression::class.java,
    KotlinBundle.messagePointer("replace.invoke.with.direct.call")
), HighPriorityAction {
    override fun applicabilityRange(element: KtDotQualifiedExpression): TextRange? {
        return if (element.isExplicitInvokeCall()) element.callExpression?.calleeExpression?.textRange else null
    }

    override fun applyTo(element: KtDotQualifiedExpression, editor: Editor?) {
        OperatorToFunctionConverter.replaceExplicitInvokeCallWithImplicit(element)
    }

    private fun KtDotQualifiedExpression.isExplicitInvokeCall(): Boolean {
        val callExpression = this.callExpression ?: return false
        if (calleeName != OperatorNameConventions.INVOKE.asString() || callExpression.typeArgumentList != null) return false

        val context = analyze(BodyResolveMode.PARTIAL)
        val referenceTarget = context[BindingContext.REFERENCE_TARGET, callExpression.referenceExpression()]
        return referenceTarget.safeAs<FunctionDescriptor>()?.isOperator == true
    }
}
