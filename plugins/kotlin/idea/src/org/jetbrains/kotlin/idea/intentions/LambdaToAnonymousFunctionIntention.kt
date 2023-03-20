// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.builtins.isSuspendFunctionType
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.moveInsideParentheses
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.BranchedFoldingUtils
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunctionDescriptor
import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.error.ErrorType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.isFlexible
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

class LambdaToAnonymousFunctionIntention : SelfTargetingIntention<KtLambdaExpression>(
    KtLambdaExpression::class.java,
    KotlinBundle.lazyMessage("convert.to.anonymous.function"),
    KotlinBundle.lazyMessage("convert.lambda.expression.to.anonymous.function")
), LowPriorityAction {
    override fun isApplicableTo(element: KtLambdaExpression, caretOffset: Int): Boolean {
        val argument = element.getStrictParentOfType<KtValueArgument>()
        val call = argument?.getStrictParentOfType<KtCallElement>()
        if (call?.getStrictParentOfType<KtFunction>()?.hasModifier(KtTokens.INLINE_KEYWORD) == true) return false

        val context = element.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
        if (call?.getResolvedCall(context)?.getParameterForArgument(argument)?.type?.isSuspendFunctionType == true) return false
        val descriptor = context[
                BindingContext.DECLARATION_TO_DESCRIPTOR,
                element.functionLiteral,
        ] as? AnonymousFunctionDescriptor ?: return false

        if (descriptor.valueParameters.any { it.isDestructuring() || it.type is ErrorType }) return false

        val lastElement = element.functionLiteral.arrow ?: element.functionLiteral.lBrace
        return caretOffset <= lastElement.endOffset
    }

    override fun applyTo(element: KtLambdaExpression, editor: Editor?) {
        val functionDescriptor = element.functionLiteral.descriptor as? AnonymousFunctionDescriptor ?: return
        val resultingFunction = convertLambdaToFunction(element, functionDescriptor) ?: return
        val argument = when (val parent = resultingFunction.parent) {
            is KtLambdaArgument -> parent
            is KtLabeledExpression -> parent.replace(resultingFunction).parent as? KtLambdaArgument
            else -> null
        } ?: return

        argument.moveInsideParentheses(argument.analyze(BodyResolveMode.PARTIAL))
    }

    private fun ValueParameterDescriptor.isDestructuring() = this is ValueParameterDescriptorImpl.WithDestructuringDeclaration

    companion object {
        fun convertLambdaToFunction(
            lambda: KtLambdaExpression,
            functionDescriptor: FunctionDescriptor,
            typeSourceCode: DescriptorRenderer = IdeDescriptorRenderers.SOURCE_CODE_TYPES,
            functionName: String = "",
            functionParameterName: (ValueParameterDescriptor, Int) -> String = { parameter, _ ->
                val parameterName = parameter.name
                if (parameterName.isSpecial) "_" else parameterName.asString().quoteIfNeeded()
            },
            typeParameters: Map<TypeConstructor, KotlinType> = emptyMap(),
            replaceElement: (KtNamedFunction) -> KtExpression = { lambda.replaced(it) }
        ): KtExpression? {
            val functionLiteral = lambda.functionLiteral
            val bodyExpression = functionLiteral.bodyExpression ?: return null

            val context = bodyExpression.analyze(BodyResolveMode.PARTIAL)
            val functionLiteralDescriptor by lazy { functionLiteral.descriptor }
            bodyExpression.collectDescendantsOfType<KtReturnExpression>().forEach {
                val targetDescriptor = it.getTargetFunctionDescriptor(context)
                if (targetDescriptor == functionDescriptor || targetDescriptor == functionLiteralDescriptor) it.labeledExpression?.delete()
            }

            val psiFactory = KtPsiFactory(lambda.project)
            val function = psiFactory.createFunction(
                KtPsiFactory.CallableBuilder(KtPsiFactory.CallableBuilder.Target.FUNCTION).apply {
                    typeParams()
                    functionDescriptor.extensionReceiverParameter?.type?.let {
                        receiver(typeSourceCode.renderType(it))
                    }

                    name(functionName)
                    for ((index, parameter) in functionDescriptor.valueParameters.withIndex()) {
                        val type = parameter.type.let { if (it.isFlexible()) it.makeNotNullable() else it }
                        val renderType = typeSourceCode.renderType(
                            getTypeFromParameters(type, typeParameters)
                        )
                        val parameterName = functionParameterName(parameter, index)
                        param(parameterName, renderType)
                    }

                    functionDescriptor.returnType?.takeIf { !it.isUnit() }?.let {
                        val lastStatement = bodyExpression.statements.lastOrNull()
                        if (lastStatement != null && lastStatement !is KtReturnExpression) {
                            val foldableReturns = BranchedFoldingUtils.getFoldableReturns(lastStatement)
                            if (foldableReturns.isNullOrEmpty()) {
                                lastStatement.replace(psiFactory.createExpressionByPattern("return $0", lastStatement))
                            }
                        }
                        val renderType = typeSourceCode.renderType(
                            getTypeFromParameters(it, typeParameters)
                        )
                        returnType(renderType)
                    } ?: noReturnType()
                    blockBody(" " + bodyExpression.text)
                }.asString()
            )

            val result = wrapInParenthesisIfNeeded(replaceElement(function), psiFactory)
            ShortenReferences.DEFAULT.process(result)

            return result
        }

        private fun getTypeFromParameters(
            type: KotlinType,
            typeParameters: Map<TypeConstructor, KotlinType>
        ): KotlinType {
            if (type.isTypeParameter())
                return typeParameters[type.constructor] ?: type
            return type
        }

        private fun wrapInParenthesisIfNeeded(expression: KtExpression, psiFactory: KtPsiFactory): KtExpression {
            val parent = expression.parent ?: return expression
            val grandParent = parent.parent ?: return expression

            if (parent is KtCallExpression && grandParent !is KtParenthesizedExpression && grandParent !is KtDeclaration) {
                return expression.replaced(psiFactory.createExpressionByPattern("($0)", expression))
            }

            return expression
        }
    }
}
