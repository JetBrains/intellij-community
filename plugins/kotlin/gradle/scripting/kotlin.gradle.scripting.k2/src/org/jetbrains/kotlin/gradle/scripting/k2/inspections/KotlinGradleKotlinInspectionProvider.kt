// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections.GradleKotlinInspectionProvider
import org.jetbrains.plugins.gradle.util.GradleConstants

class KotlinGradleKotlinInspectionProvider : GradleKotlinInspectionProvider {

    override fun isRedundantKotlinStdLibInspectionAvailable(file: PsiFile): Boolean {
        return FileUtilRt.extensionEquals(file.name, GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION)
    }

    override fun getRedundantKotlinStdLibInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return RedundantKotlinStdLibInspectionVisitor(holder)
    }
}