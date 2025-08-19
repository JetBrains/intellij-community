// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.gradle.configuration.readGradleProperty

class RedundantKotlinStdLibInspection : LocalInspectionTool() {
    override fun isAvailableForFile(file: PsiFile): Boolean {
        val kotlinStdlibDependencyByDefaultProp = readGradleProperty(file.project, "kotlin.stdlib.default.dependency")
        // if the property is not set (null value), then the inspection should be active
        // and only if it is "false" should it be deactivated as that is how it works in Gradle
        if (kotlinStdlibDependencyByDefaultProp == "false") return false

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