// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor

class GradleForeignDelegateInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val language = holder.file.language
    val inspectionProvider = GradleDslInspectionProvider.INSTANCE.forLanguage(language) ?: return PsiElementVisitor.EMPTY_VISITOR
    return inspectionProvider.getForeignDelegateInspectionVisitor(holder, isOnTheFly)
  }

}