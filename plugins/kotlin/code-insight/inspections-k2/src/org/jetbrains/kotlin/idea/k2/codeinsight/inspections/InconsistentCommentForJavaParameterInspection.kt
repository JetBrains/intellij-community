// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.getArgumentNameComments
import org.jetbrains.kotlin.idea.codeinsights.impl.base.getBlockCommentWithName
import org.jetbrains.kotlin.idea.codeinsights.impl.base.hasArgumentNameComments
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isExpectedArgumentNameComment
import org.jetbrains.kotlin.psi.*

internal class InconsistentCommentForJavaParameterInspection: LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitCallExpression(callExpression: KtCallExpression) = callExpression.check()
        override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) = call.check()
        override fun visitConstructorDelegationCall(call: KtConstructorDelegationCall) = call.check()
        override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) = annotationEntry.check()

        private fun KtCallElement.check() {
            if (!hasArgumentNameComments()) return

            analyze(this) {
                val expectedNameComments = getArgumentNameComments(this@check) ?: return
                expectedNameComments.forEach { (argumentPointer, expected) ->
                    val actualComment = argumentPointer.element?.getBlockCommentWithName() ?: return@forEach
                    if (!actualComment.isExpectedArgumentNameComment(expected)) {
                        holder.registerProblem(
                            actualComment,
                            KotlinBundle.message("inspection.message.inconsistent.parameter.name.for.0", expected.argumentName.asString()),
                            CorrectNamesInCommentsToJavaCallArgumentsFix(expected.comment)
                        )
                    }
                }
            }
        }
    }

    private class CorrectNamesInCommentsToJavaCallArgumentsFix(private val commentedParameterName: String) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("intention.name.use.correct.parameter.name")
        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val comment = descriptor.psiElement as? PsiComment ?: return
            val factory = KtPsiFactory(project)
            comment.replace(factory.createComment(commentedParameterName))
        }
    }
}