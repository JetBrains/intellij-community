// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GradleKotlinInspectionProvider {
    companion object {
        val INSTANCE: LanguageExtension<GradleKotlinInspectionProvider> =
            LanguageExtension<GradleKotlinInspectionProvider>("org.jetbrains.kotlin.idea.gradleKotlinInspectionProvider")
    }

    /**
     * @see RedundantKotlinStdLibInspection
     */
    fun isRedundantKotlinStdLibInspectionAvailable(file: PsiFile): Boolean
    fun getRedundantKotlinStdLibInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor
}