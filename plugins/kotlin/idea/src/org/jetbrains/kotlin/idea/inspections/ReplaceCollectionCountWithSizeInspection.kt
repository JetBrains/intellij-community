// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.intentions.receiverType
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

class ReplaceCollectionCountWithSizeInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return callExpressionVisitor(fun(callExpression: KtCallExpression) {
            if (callExpression.calleeExpression?.text != "count" || callExpression.valueArguments.isNotEmpty()) return
            val context = callExpression.analyze(BodyResolveMode.PARTIAL)
            if (!callExpression.isCalling(FqName("kotlin.collections.count"))) return
            val receiverType = callExpression.getResolvedCall(context)?.resultingDescriptor?.receiverType()?: return
            if (KotlinBuiltIns.isIterableOrNullableIterable(receiverType)) return
            holder.registerProblem(
                callExpression,
                KotlinBundle.message("could.be.replaced.with.size"),
                ReplaceCollectionCountWithSizeQuickFix()
            )
        })
    }
}

class ReplaceCollectionCountWithSizeQuickFix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("replace.collection.count.with.size.quick.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as KtCallExpression
        element.replace(KtPsiFactory(element).createExpression("size"))
    }
}
