// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.getDeepestSuperDeclarations
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.MODIFIER_KEYWORDS_ARRAY
import org.jetbrains.kotlin.lexer.KtTokens.OVERRIDE_KEYWORD
import org.jetbrains.kotlin.load.java.JavaDescriptorVisibilities
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassDescriptor
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import org.jetbrains.kotlin.synthetic.canBePropertyAccessor
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

class KotlinRedundantOverrideInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        namedFunctionVisitor(fun(function) {
            val funKeyword = function.funKeyword ?: return
            val modifierList = function.modifierList ?: return
            if (!modifierList.hasModifier(OVERRIDE_KEYWORD)) return
            if (MODIFIER_EXCLUDE_OVERRIDE.any { modifierList.hasModifier(it) }) return
            if (KotlinPsiHeuristics.hasNonSuppressAnnotations(function)) return

            val qualifiedExpression = function.qualifiedExpression() ?: return
            val superExpression = qualifiedExpression.receiverExpression as? KtSuperExpression ?: return
            if (superExpression.superTypeQualifier != null) return

            val superCallElement = qualifiedExpression.selectorExpression as? KtCallElement ?: return
            if (!isSameFunctionName(superCallElement, function)) return
            if (!isSameArguments(superCallElement, function)) return

            val context = superCallElement.analyze()
            val functionDescriptor = context[BindingContext.FUNCTION, function] ?: return
            val functionParams = functionDescriptor.valueParameters
            val superCallDescriptor = superCallElement.getResolvedCall(context)?.resultingDescriptor ?: return
            val superCallFunctionParams = superCallDescriptor.valueParameters

            if (functionParams.size == superCallFunctionParams.size
                && functionParams.zip(superCallFunctionParams).any { it.first.type != it.second.type }
            ) return

            if (function.containingClassOrObject?.isData() == true &&
                (superCallDescriptor.isMethodOfAny() || superCallDescriptor.isOverridingMethodOfAny())
            ) return

            if (function.hasDerivedProperty(functionDescriptor, context)) return

            val overriddenDescriptors = getOverriddenDescriptors(functionDescriptor)
            if (overriddenDescriptors.any { it is JavaMethodDescriptor && it.visibility == JavaDescriptorVisibilities.PACKAGE_VISIBILITY }) return
            if (overriddenDescriptors.any { it.modality == Modality.ABSTRACT }) {
                if (superCallDescriptor.isMethodOfAny()) return
                if (superCallDescriptor.isOverridingMethodOfAny() && !superCallDescriptor.isImplementedInContainingClass()) return
            }
            if (function.isAmbiguouslyDerived(overriddenDescriptors, context)) return

            val range = TextRange(modifierList.startOffsetInParent, funKeyword.endOffset - function.startOffset)
            val descriptor = holder.manager.createProblemDescriptor(
                function,
                range,
                KotlinBundle.message("redundant.overriding.method"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
                RedundantOverrideFix()
            )
            holder.registerProblem(descriptor)
        })

    private fun KtNamedFunction.qualifiedExpression(): KtDotQualifiedExpression? {
        val bodyExpression = bodyExpression ?: return null
        return when (bodyExpression) {
            is KtDotQualifiedExpression -> bodyExpression
            is KtBlockExpression -> {
                when (val body = bodyExpression.statements.singleOrNull()) {
                    is KtReturnExpression -> body.returnedExpression
                    is KtDotQualifiedExpression -> body.takeIf { _ ->
                        typeReference.let { it == null || it.text == "Unit" }
                    }

                    else -> null
                }
            }

            else -> null
        } as? KtDotQualifiedExpression
    }

    private fun isSameArguments(superCallElement: KtCallElement, function: KtNamedFunction): Boolean {
        val arguments = superCallElement.valueArguments
        val parameters = function.valueParameters
        if (arguments.size != parameters.size) return false
        return arguments.zip(parameters).all { (argument, parameter) ->
            argument.getArgumentExpression()?.text == parameter.name
        }
    }

    private fun isSameFunctionName(superSelectorExpression: KtCallElement, function: KtNamedFunction): Boolean {
        val superCallMethodName = superSelectorExpression.getCallNameExpression()?.text ?: return false
        return function.name == superCallMethodName
    }

    private fun CallableDescriptor.isMethodOfAny(): Boolean = fqNameSafe in METHODS_OF_ANY

    private fun CallableDescriptor.isOverridingMethodOfAny(): Boolean =
        (this as? CallableMemberDescriptor)?.getDeepestSuperDeclarations().orEmpty().any { it.isMethodOfAny() }

    private fun CallableDescriptor.isImplementedInContainingClass(): Boolean =
        (containingDeclaration as? LazyClassDescriptor)?.declaredCallableMembers.orEmpty().any { it == this }

    private fun KtNamedFunction.hasDerivedProperty(functionDescriptor: FunctionDescriptor?, context: BindingContext): Boolean {
        if (functionDescriptor == null) return false
        val functionName = nameAsName ?: return false
        if (!canBePropertyAccessor(functionName.asString())) return false
        val functionType = functionDescriptor.returnType
        val isSetter = functionType?.isUnit() == true
        val valueParameters = valueParameters
        val singleValueParameter = valueParameters.singleOrNull()
        if (isSetter && singleValueParameter == null || !isSetter && valueParameters.isNotEmpty()) return false
        val propertyType =
            (if (isSetter) context[BindingContext.VALUE_PARAMETER, singleValueParameter]?.type else functionType)?.makeNotNullable()
        return SyntheticJavaPropertyDescriptor.propertyNamesByAccessorName(functionName).any {
            val propertyName = it.asString()
            containingClassOrObject?.declarations?.find { d ->
                d is KtProperty && d.name == propertyName && d.resolveToDescriptorIfAny()?.type?.makeNotNullable() == propertyType
            } != null
        }
    }

    private fun getOverriddenDescriptors(functionDescriptor: SimpleFunctionDescriptor): Collection<FunctionDescriptor> {
        fun FunctionDescriptor.listClosestNonFakeSupersOrSelf(): Collection<FunctionDescriptor> {
            if (kind != CallableMemberDescriptor.Kind.FAKE_OVERRIDE) return listOf(this)
            this.overriddenDescriptors.singleOrNull()?.let { return it.listClosestNonFakeSupersOrSelf() }
            return this.overriddenDescriptors
        }

        var overriddenDescriptors = functionDescriptor.original.overriddenDescriptors
        if (overriddenDescriptors.size == 1) {
            overriddenDescriptors = overriddenDescriptors.single().listClosestNonFakeSupersOrSelf()
        }
        return overriddenDescriptors
    }

    private class RedundantOverrideFix : LocalQuickFix {
        override fun getName() = KotlinBundle.message("redundant.override.fix.text")
        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            descriptor.psiElement.delete()
        }
    }
}

private val MODIFIER_EXCLUDE_OVERRIDE: List<KtModifierKeywordToken> = MODIFIER_KEYWORDS_ARRAY.asList() - OVERRIDE_KEYWORD
private val METHODS_OF_ANY: List<FqName> = listOf("equals", "hashCode", "toString").map { FqName("kotlin.Any.$it") }

private fun KtNamedFunction.isAmbiguouslyDerived(overriddenDescriptors: Collection<FunctionDescriptor>?, context: BindingContext): Boolean {
    if (overriddenDescriptors == null || overriddenDescriptors.size < 2) return false
    // Two+ functions
    // At least one default in interface or abstract in class, or just something from Java
    if (overriddenDescriptors.any { overriddenFunction ->
            overriddenFunction is JavaMethodDescriptor || when ((overriddenFunction.containingDeclaration as? ClassDescriptor)?.kind) {
                ClassKind.CLASS -> overriddenFunction.modality == Modality.ABSTRACT
                ClassKind.INTERFACE -> overriddenFunction.modality != Modality.ABSTRACT
                else -> false
            }

        }
    ) return true

    val delegatedSuperTypeEntries =
        containingClassOrObject?.superTypeListEntries?.filterIsInstance<KtDelegatedSuperTypeEntry>() ?: return false
    if (delegatedSuperTypeEntries.isEmpty()) return false
    val delegatedSuperDeclarations = delegatedSuperTypeEntries.mapNotNull { entry ->
        context[BindingContext.TYPE, entry.typeReference]?.constructor?.declarationDescriptor
    }
    return overriddenDescriptors.any {
        it.containingDeclaration in delegatedSuperDeclarations
    }
}
