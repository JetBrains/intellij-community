// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction

const val KOTLIN_JVM_PACKAGE: String = "kotlin.jvm."

internal class KotlinJvmAnnotationInJavaInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): JavaElementVisitor =
        object : JavaElementVisitor() {
            override fun visitAnnotation(annotation: PsiAnnotation) {
                val qualifiedName = annotation.qualifiedName ?: return
                if (!qualifiedName.startsWith(KOTLIN_JVM_PACKAGE)) return

                val annotationName = qualifiedName.removePrefix(KOTLIN_JVM_PACKAGE)
                val action = RemoveAnnotationFix(annotation)
                val fix = LocalQuickFix.from(action) ?: error("Broken contract: unexpected null quick fix for non-null action")
                holder.registerProblem(
                    annotation,
                    KotlinBundle.message("inspection.kotlin.jvm.annotation.in.java.description", "@$annotationName"),
                    fix,
                )
            }
        }

    private class RemoveAnnotationFix(
        element: PsiAnnotation
    ) : KotlinPsiUpdateModCommandAction.ElementContextless<PsiAnnotation>(element) {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("fix.remove.annotation.text")

        override fun invoke(
            context: ActionContext,
            element: PsiAnnotation,
            updater: ModPsiUpdater
        ): Unit = element.delete()
    }
}
