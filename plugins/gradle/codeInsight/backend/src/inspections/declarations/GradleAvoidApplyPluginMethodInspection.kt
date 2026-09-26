// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.backend.inspections.declarations

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.gradle.codeInsight.backend.inspections.GradleDslInspectionProvider
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

class GradleAvoidApplyPluginMethodInspection : LocalInspectionTool() {
  override fun isAvailableForFile(file: PsiFile): Boolean {
    val language = file.language
    val inspectionProvider = GradleDslInspectionProvider.INSTANCE.forLanguage(language) ?: return false
    return inspectionProvider.isAvoidApplyPluginMethodInspectionAvailable(file)
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val language = holder.file.language
    val inspectionProvider = GradleDslInspectionProvider.INSTANCE.forLanguage(language) ?: return PsiElementVisitor.EMPTY_VISITOR
    return inspectionProvider.getAvoidApplyPluginMethodInspectionVisitor(holder, isOnTheFly)
  }
}