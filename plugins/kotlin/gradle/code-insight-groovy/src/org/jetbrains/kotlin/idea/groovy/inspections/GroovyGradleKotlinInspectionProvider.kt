// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.groovy.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.extensions.gradle.KotlinGradleConstants
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections.GradleKotlinInspectionProvider
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor

class GroovyGradleKotlinInspectionProvider : GradleKotlinInspectionProvider {
    override fun isRedundantKotlinStdLibInspectionAvailable(file: PsiFile): Boolean {
        return FileUtilRt.extensionEquals(file.name, KotlinGradleConstants.GROOVY_EXTENSION)
    }

    override fun getRedundantKotlinStdLibInspectionVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return GroovyPsiElementVisitor(RedundantKotlinStdLibInspectionVisitor(holder))
    }
}