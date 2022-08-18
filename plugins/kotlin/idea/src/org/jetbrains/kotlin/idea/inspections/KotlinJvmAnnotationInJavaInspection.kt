// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

class KotlinJvmAnnotationInJavaInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : JavaElementVisitor() {
        private val KOTLIN_JVM_PACKAGE = "kotlin.jvm."

        override fun visitAnnotation(annotation: PsiAnnotation) {
            val qualifiedName = annotation.qualifiedName ?: return
            if (qualifiedName.startsWith(KOTLIN_JVM_PACKAGE)) {
                val annotationName = qualifiedName.removePrefix(KOTLIN_JVM_PACKAGE)
                holder.registerProblem(
                    annotation,
                    KotlinBundle.message("inspection.kotlin.jvm.annotation.in.java.description", "@$annotationName"),
                    RemoveAnnotationFix()
                )
            }
        }
    }

    private class RemoveAnnotationFix : LocalQuickFix {
        override fun getFamilyName() = KotlinBundle.message("fix.remove.annotation.text")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            descriptor.psiElement.delete()
        }
    }
}
