// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.intentions.AddNamesInCommentToJavaCallArgumentsIntention
import org.jetbrains.kotlin.idea.intentions.AddNamesInCommentToJavaCallArgumentsIntention.Companion.blockCommentWithName
import org.jetbrains.kotlin.idea.intentions.AddNamesInCommentToJavaCallArgumentsIntention.Companion.isParameterNameComment
import org.jetbrains.kotlin.idea.intentions.AddNamesInCommentToJavaCallArgumentsIntention.Companion.toParameterNameComment
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class InconsistentCommentForJavaParameterInspection: AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitCallExpression(callExpression: KtCallExpression) = callExpression.check()

        override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) = call.check()

        override fun visitConstructorDelegationCall(call: KtConstructorDelegationCall) = call.check()

        override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) = annotationEntry.check()

        private fun KtCallElement.check() {
            val valueDescriptorByValueArgument = AddNamesInCommentToJavaCallArgumentsIntention.resolveValueParameterDescriptors(
                this,
                canAddNameComments = false
            ) ?: return

            for ((argument, descriptor)  in valueDescriptorByValueArgument) {
                val comment = argument.blockCommentWithName() ?: continue
                if (comment.isParameterNameComment(descriptor)) continue
                holder.registerProblem(
                    comment,
                    KotlinBundle.message("inspection.message.inconsistent.parameter.name.for.0", descriptor.name.asString()),
                    CorrectNamesInCommentsToJavaCallArgumentsFix(descriptor.toParameterNameComment())
                )
            }
        }
    }

    class CorrectNamesInCommentsToJavaCallArgumentsFix(private val commentedParameterName: String) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("intention.name.use.correct.parameter.name")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val comment = descriptor.psiElement.safeAs<PsiComment>() ?: return
            val factory = KtPsiFactory(project)
            comment.replace(factory.createComment(commentedParameterName))
        }

    }
}