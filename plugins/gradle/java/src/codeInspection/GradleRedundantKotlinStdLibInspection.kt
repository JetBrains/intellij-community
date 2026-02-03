// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.service.resolve.gradlePropertiesStream

class GradleRedundantKotlinStdLibInspection : LocalInspectionTool() {
  override fun isAvailableForFile(file: PsiFile): Boolean {
    val kotlinStdlibDefaultDependencyProp = gradlePropertiesStream(file).firstNotNullOfOrNull {
      it.findPropertyByKey("kotlin.stdlib.default.dependency")?.value
    }
    // the default value is "true"
    if (kotlinStdlibDefaultDependencyProp == "false") return false

    val language = file.language
    val inspectionProvider = GradleDslInspectionProvider.INSTANCE.forLanguage(language) ?: return false
    return inspectionProvider.isRedundantKotlinStdLibInspectionAvailable(file)
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val language = holder.file.language
    val inspectionProvider = GradleDslInspectionProvider.INSTANCE.forLanguage(language) ?: return PsiElementVisitor.EMPTY_VISITOR
    return inspectionProvider.getRedundantKotlinStdLibInspectionVisitor(holder, isOnTheFly)
  }
}