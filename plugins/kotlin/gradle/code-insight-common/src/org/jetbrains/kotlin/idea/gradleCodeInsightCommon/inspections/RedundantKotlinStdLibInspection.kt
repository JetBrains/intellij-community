// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

class RedundantKotlinStdLibInspection : LocalInspectionTool() {
    override fun isAvailableForFile(file: PsiFile): Boolean {
        val language = file.language
        val inspectionProvider = GradleKotlinInspectionProvider.INSTANCE.forLanguage(language) ?: return false
        return inspectionProvider.isRedundantKotlinStdLibInspectionAvailable(file)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val language = holder.file.language
        val inspectionProvider = GradleKotlinInspectionProvider.INSTANCE.forLanguage(language) ?: return PsiElementVisitor.EMPTY_VISITOR
        return inspectionProvider.getRedundantKotlinStdLibInspectionVisitor(holder, isOnTheFly)
    }
}