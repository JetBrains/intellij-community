// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.intentions.AddNamesInCommentToJavaCallArgumentsIntention.Companion.blockCommentWithName
import org.jetbrains.kotlin.idea.intentions.AddNamesInCommentToJavaCallArgumentsIntention.Companion.hasBlockCommentWithName
import org.jetbrains.kotlin.idea.intentions.AddNamesInCommentToJavaCallArgumentsIntention.Companion.resolve
import org.jetbrains.kotlin.idea.intentions.AddNamesInCommentToJavaCallArgumentsIntention.Companion.toCommentedParameterName
import org.jetbrains.kotlin.load.java.descriptors.JavaClassConstructorDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class InconsistentCommentForJavaParameterInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        callExpressionVisitor(fun(callExpression) {
            val arguments = callExpression.valueArguments.filterNot { it is KtLambdaArgument }
            if (arguments.isEmpty() || arguments.any { it.isNamed() } || arguments.none { it.hasBlockCommentWithName() }) return

            val resolvedCall = callExpression.resolveToCall() ?: return

            if (arguments.size != arguments.resolve(resolvedCall).size) return

            resolvedCall.candidateDescriptor.takeIf { it is JavaMethodDescriptor || it is JavaClassConstructorDescriptor } ?: return

            val valueDescriptorByValueArgument: Map<KtValueArgument, ValueParameterDescriptor> =
                callExpression.valueArguments.resolve(resolvedCall).toMap()

            for (valueArgument in arguments) {
                if (!valueArgument.hasBlockCommentWithName()) continue

                val comment = valueArgument.blockCommentWithName() ?: continue
                val valueDescriptor = valueDescriptorByValueArgument[valueArgument] ?: continue
                val commentedParameterName = valueDescriptor.toCommentedParameterName()
                if (commentedParameterName == comment.text) continue
                holder.registerProblem(
                    comment,
                    KotlinBundle.message("inspection.message.inconsistent.parameter.name.for.0", valueDescriptor.name.asString()),
                    ProblemHighlightType.WARNING,
                    CorrectNamesInCommentsToJavaCallArgumentsFix(commentedParameterName)
                )
            }
        })

    class CorrectNamesInCommentsToJavaCallArgumentsFix(private val commentedParameterName: String) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("intention.name.correct.parameter.name")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val comment = descriptor.psiElement.safeAs<PsiComment>() ?: return
            val factory = KtPsiFactory(project)
            comment.replace(factory.createComment(commentedParameterName))
        }

    }
}