// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class RedundantOptionalArgumentInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = valueArgumentVisitor {
        if (it.isRedundant()) {
            holder.registerProblem(
                it,
                KotlinBundle.message("inspection.redundant.optional.argument.annotation"),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                RemoveArgumentFix()
            )
        }
    }

    private fun KtValueArgument.isRedundant(): Boolean {
        val expression = getArgumentExpression().takeIf { it is KtConstantExpression || it is KtNameReferenceExpression } ?: return false
        val arguments = getStrictParentOfType<KtValueArgumentList>()?.arguments ?: return false
        if (this != arguments.lastOrNull() && !arguments.all { it.isNamed() }) return false

        val context = analyze(BodyResolveMode.PARTIAL)
        val parameter = correspondingParameter(context) ?: return false
        val expectedType = parameter.type
        val argumentValue = ConstantExpressionEvaluator.getConstant(expression, context)?.getValue(expectedType) ?: return false
        return argumentValue == parameter.defaultValue(expectedType)
    }

    private fun KtValueArgument.correspondingParameter(context: BindingContext): ValueParameterDescriptor? {
        val resolvedCall = getStrictParentOfType<KtCallElement>()?.getResolvedCall(context) ?: return null
        val argumentMatch = resolvedCall.getArgumentMapping(this).safeAs<ArgumentMatch>()?.takeIf { !it.isError() } ?: return null
        return argumentMatch.valueParameter
    }

    private fun ValueParameterDescriptor.defaultValue(expectedType: KotlinType): Any? {
        val defaultValue = DescriptorToSourceUtils.descriptorToDeclaration(this)?.safeAs<KtParameter>()?.defaultValue ?: return null
        return ConstantExpressionEvaluator.getConstant(defaultValue, defaultValue.safeAnalyzeNonSourceRootCode())?.getValue(expectedType)
    }

    private class RemoveArgumentFix : LocalQuickFix {
        override fun getName() = KotlinBundle.message("fix.remove.argument.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val argument = descriptor.psiElement.safeAs<KtValueArgument>() ?: return
            argument.getStrictParentOfType<KtValueArgumentList>()?.removeArgument(argument)
        }
    }
}
