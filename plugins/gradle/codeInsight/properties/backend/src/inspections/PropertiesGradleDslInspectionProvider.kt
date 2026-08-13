// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.properties.backend.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.gradle.codeInsight.backend.inspections.GradleDslInspectionProvider
import com.intellij.gradle.codeInsight.properties.backend.inspections.visitors.GradleLatestMinorVersionInspectionVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_WRAPPER_PROPERTIES_FILE_NAME

internal class PropertiesGradleDslInspectionProvider : GradleDslInspectionProvider {
  override fun isLatestMinorVersionInspectionAvailable(file: PsiFile) =
    file.name.equals(GRADLE_WRAPPER_PROPERTIES_FILE_NAME, ignoreCase = !file.virtualFile.isCaseSensitive)

  override fun getLatestMinorVersionInspectionVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
    GradleLatestMinorVersionInspectionVisitor(holder)
}
