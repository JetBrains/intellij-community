// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections.GradleKotlinInspectionProvider

class KotlinGradleKotlinInspectionProvider : GradleKotlinInspectionProvider {

    override fun isRedundantKotlinStdLibInspectionAvailable(file: PsiFile): Boolean {
        return FileUtilRt.extensionEquals(file.name, "gradle.kts")
    }

    override fun getRedundantKotlinStdLibInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return RedundantKotlinStdLibInspectionVisitor(holder)
    }
}