// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

const val KOTLIN_JVM_PACKAGE: String = "kotlin.jvm."

internal class KotlinJvmAnnotationInJavaInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): JavaElementVisitor =
        object : JavaElementVisitor() {
            override fun visitAnnotation(annotation: PsiAnnotation) {
                val qualifiedName = annotation.qualifiedName ?: return
                if (!qualifiedName.startsWith(KOTLIN_JVM_PACKAGE)) return

                val annotationName = qualifiedName.removePrefix(KOTLIN_JVM_PACKAGE)
                holder.registerProblem(
                    annotation,
                    KotlinBundle.message("inspection.kotlin.jvm.annotation.in.java.description", "@$annotationName"),
                    RemoveAnnotationFix()
                )
            }
        }

    private class RemoveAnnotationFix : LocalQuickFix {
        override fun getFamilyName() =
            KotlinBundle.message("fix.remove.annotation.text")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) =
            descriptor.psiElement.delete()
    }
}
