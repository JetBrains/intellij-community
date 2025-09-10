// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueDescriptor
import org.jetbrains.kotlin.diagnostics.Errors.*
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyzeInContext
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.RemoveExplicitTypeArgumentsUtils
import org.jetbrains.kotlin.idea.project.builtIns
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoBefore
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsStatement
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker

@Suppress("DEPRECATION")
class RemoveExplicitTypeArgumentsInspection : IntentionBasedInspection<KtTypeArgumentList>(RemoveExplicitTypeArgumentsIntention::class) {
    override val problemText: String = KotlinBundle.message("explicit.type.arguments.can.be.inferred")

    override fun additionalFixes(element: KtTypeArgumentList): List<LocalQuickFix>? {
        val declaration = element.getStrictParentOfType<KtCallableDeclaration>() ?: return null
        if (!RemoveExplicitTypeIntention.Holder.isApplicableTo(declaration)) return null
        return listOf(RemoveExplicitTypeFix(declaration.nameAsSafeName.asString()))
    }

    private class RemoveExplicitTypeFix(private val declarationName: String) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("remove.explicit.type.specification.from.0", declarationName)

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtTypeArgumentList ?: return
            val declaration = element.getStrictParentOfType<KtCallableDeclaration>() ?: return
            RemoveExplicitTypeIntention.Holder.removeExplicitType(declaration)
        }
    }
}

/**
 * Related tests:
 * [org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.RemoveExplicitTypeArguments]
 * [org.jetbrains.kotlin.idea.intentions.IntentionTestGenerated.RemoveExplicitTypeArguments]
 * [org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated.DeprecatedSymbolUsage.Imports.testAddImportForOperator]
 * [org.jetbrains.kotlin.idea.quickfix.QuickFixTestGenerated.DeprecatedSymbolUsage.TypeArguments.WholeProject.testClassConstructor]
 * [org.jetbrains.kotlin.idea.refactoring.inline.InlineTestGenerated]
 * [org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated.ExtractFunction]
 * [org.jetbrains.kotlin.nj2k.*]
 */
class RemoveExplicitTypeArgumentsIntention : SelfTargetingOffsetIndependentIntention<KtTypeArgumentList>(
    KtTypeArgumentList::class.java,
    KotlinBundle.messagePointer("remove.explicit.type.arguments")
) {
    companion object {
        private val INLINE_REIFIED_FUNCTIONS_WITH_INSIGNIFICANT_TYPE_ARGUMENTS: Set<String> = setOf(
            "kotlin.arrayOf"
        )

        fun isApplicableTo(element: KtTypeArgumentList, approximateFlexible: Boolean): Boolean {
            val callExpression = element.parent as? KtCallExpression ?: return false
            if (!RemoveExplicitTypeArgumentsUtils.isApplicableByPsi(callExpression)) return false

            val resolutionFacade = callExpression.getResolutionFacade()
            val bindingContext = resolutionFacade.analyze(callExpression, BodyResolveMode.PARTIAL_WITH_CFA)
            val originalCall = callExpression.getResolvedCall(bindingContext) ?: return false

            originalCall.resultingDescriptor.let {
                if (it.isInlineFunctionWithReifiedTypeParameters() &&
                    it.fqNameSafe.asString() !in INLINE_REIFIED_FUNCTIONS_WITH_INSIGNIFICANT_TYPE_ARGUMENTS
                ) return false
            }

            val (contextExpression, expectedType) = findContextToAnalyze(callExpression, bindingContext)
            val resolutionScope = contextExpression.getResolutionScope(bindingContext, resolutionFacade)

            val key = Key<Unit>("RemoveExplicitTypeArgumentsIntention")
            callExpression.putCopyableUserData(key, Unit)
            val expressionToAnalyze = contextExpression.copied()
            callExpression.putCopyableUserData(key, null)

            val newCallExpression = expressionToAnalyze.findDescendantOfType<KtCallExpression> { it.getCopyableUserData(key) != null }!!
            newCallExpression.typeArgumentList!!.delete()

            val newBindingContext = expressionToAnalyze.analyzeInContext(
                resolutionScope,
                contextExpression,
                trace = DelegatingBindingTrace(bindingContext, "Temporary trace"),
                dataFlowInfo = bindingContext.getDataFlowInfoBefore(contextExpression),
                expectedType = expectedType ?: TypeUtils.NO_EXPECTED_TYPE,
                isStatement = contextExpression.isUsedAsStatement(bindingContext)
            )
            val newDiagnostics = newBindingContext.diagnostics

            val newCallee = newCallExpression.calleeExpression ?: return false
            if (newDiagnostics.forElement(newCallee).any { it.factory == IMPLICIT_NOTHING_TYPE_ARGUMENT_IN_RETURN_POSITION }) return false

            val newCall = newCallExpression.getResolvedCall(newBindingContext) ?: return false

            val args = originalCall.typeArguments
            val newArgs = newCall.typeArguments

            fun equalTypes(type1: KotlinType, type2: KotlinType): Boolean {
                return if (approximateFlexible) {
                    KotlinTypeChecker.DEFAULT.equalTypes(type1, type2)
                } else {
                    type1 == type2
                }
            }

            return args.size == newArgs.size &&
                    args.values.zip(newArgs.values).all { (argType, newArgType) -> equalTypes(argType, newArgType) } &&
                    (newDiagnostics.asSequence() - bindingContext.diagnostics).none {
                        it.factory == INFERRED_INTO_DECLARED_UPPER_BOUNDS || it.factory == UNRESOLVED_REFERENCE ||
                                it.factory == BUILDER_INFERENCE_STUB_RECEIVER ||
                                // just for sure because its builder inference related
                                it.factory == COULD_BE_INFERRED_ONLY_WITH_UNRESTRICTED_BUILDER_INFERENCE
                    }
        }

        private fun CallableDescriptor.isInlineFunctionWithReifiedTypeParameters(): Boolean =
            this is FunctionDescriptor && isInline && typeParameters.any { it.isReified }

        fun findContextToAnalyze(expression: KtExpression, bindingContext: BindingContext): Pair<KtExpression, KotlinType?> {
            for (element in expression.parentsWithSelf) {
                if (element !is KtExpression || element is KtEnumEntry) continue

                if (element.getQualifiedExpressionForSelector() != null) continue
                if (element is KtFunctionLiteral) continue
                if (!element.isUsedAsExpression(bindingContext)) return element to null

                when (val parent = element.parent) {
                    is KtNamedFunction -> {
                        val expectedType = if (element == parent.bodyExpression && !parent.hasBlockBody() && parent.hasDeclaredReturnType())
                            (bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, parent] as? FunctionDescriptor)?.returnType
                        else
                            null
                        return element to expectedType
                    }

                    is KtVariableDeclaration -> {
                        val expectedType = if (element == parent.initializer && parent.typeReference != null)
                            (bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, parent] as? ValueDescriptor)?.type
                        else
                            null
                        return element to expectedType
                    }

                    is KtParameter -> {
                        val expectedType = if (element == parent.defaultValue)
                            (bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, parent] as? ValueDescriptor)?.type
                        else
                            null
                        return element to expectedType
                    }

                    is KtPropertyAccessor -> {
                        val property = parent.parent as KtProperty
                        val expectedType = when {
                            element != parent.bodyExpression || parent.hasBlockBody() -> null
                            parent.isSetter -> parent.builtIns.unitType
                            property.typeReference == null -> null
                            else -> (bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, parent] as? FunctionDescriptor)?.returnType
                        }
                        return element to expectedType
                    }
                }
            }

            return expression to null
        }
    }

    override fun isApplicableTo(element: KtTypeArgumentList): Boolean = isApplicableTo(element, approximateFlexible = false)

    override fun applyTo(element: KtTypeArgumentList, editor: Editor?) = RemoveExplicitTypeArgumentsUtils.applyTo(element)
}
