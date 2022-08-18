// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.search.searches.ReferencesSearch
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsResultOfLambda
import org.jetbrains.kotlin.resolve.descriptorUtil.isSubclassOf
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.isNothing

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class KotlinThrowableNotThrownInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = callExpressionVisitor(fun(callExpression) {
        val calleeExpression = callExpression.calleeExpression ?: return
        if (!calleeExpression.text.let { it.contains("Exception") || it.contains("Error") }) return
        if (TestUtils.isInTestSourceContent(callExpression)) return
        val resultingDescriptor = callExpression.resolveToCall()?.resultingDescriptor ?: return
        val type = resultingDescriptor.returnType ?: return
        if (type.isNothing() || type.isNullable()) return
        val classDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return
        if (!classDescriptor.isSubclassOf(DefaultBuiltIns.Instance.throwable)) return
        if (callExpression.isUsed()) return

        val description = if (resultingDescriptor is ConstructorDescriptor) {
            KotlinBundle.message("throwable.instance.0.is.not.thrown", calleeExpression.text)
        } else {
            KotlinBundle.message("result.of.0.call.is.not.thrown", calleeExpression.text)
        }
        holder.registerProblem(calleeExpression, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    })

    private fun KtExpression.isUsed(): Boolean {
        val context = analyze(BodyResolveMode.PARTIAL_WITH_CFA)
        if (!isUsedAsExpression(context)) return false
        if (isUsedAsResultOfLambda(context)) return true
        val property = getParentOfTypes(
            true,
            KtThrowExpression::class.java,
            KtReturnExpression::class.java,
            KtProperty::class.java
        ) as? KtProperty ?: return true
        return !property.isLocal || ReferencesSearch.search(property).any()
    }
}