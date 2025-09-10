// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.refactoring.util.specifyExplicitLambdaSignature
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.isError

open class SpecifyExplicitLambdaSignatureIntention : SelfTargetingOffsetIndependentIntention<KtLambdaExpression>(
    KtLambdaExpression::class.java, KotlinBundle.messagePointer("specify.explicit.lambda.signature")
), LowPriorityAction {
    override fun isApplicableTo(element: KtLambdaExpression): Boolean {
        if (element.functionLiteral.arrow != null && element.valueParameters.all { it.typeReference != null }) return false
        val functionDescriptor = element.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)[BindingContext.FUNCTION, element.functionLiteral] ?: return false
        return functionDescriptor.valueParameters.none { it.type.isError }
    }

    override fun applyTo(element: KtLambdaExpression, editor: Editor?) {
        Holder.applyTo(element)
    }

    object Holder {
        fun applyTo(element: KtLambdaExpression) {
            val functionLiteral = element.functionLiteral
            val functionDescriptor = element.analyze(BodyResolveMode.PARTIAL)[BindingContext.FUNCTION, functionLiteral]!!

            specifyExplicitLambdaSignature(element, functionDescriptor.valueParameters
                .asSequence()
                .mapIndexed { index, parameterDescriptor ->
                    parameterDescriptor.render(psiName = functionLiteral.valueParameters.getOrNull(index)?.let {
                        it.name ?: it.destructuringDeclaration?.text
                    })
                }
                .joinToString())
        }
    }
}

private fun ValueParameterDescriptor.render(psiName: String?): String = IdeDescriptorRenderers.SOURCE_CODE.let {
    "${psiName ?: it.renderName(name, true)}: ${it.renderType(type)}"
}
