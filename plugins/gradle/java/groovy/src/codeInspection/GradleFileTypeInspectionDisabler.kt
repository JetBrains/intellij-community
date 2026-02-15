// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.groovy.codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.gradle.java.groovy.service.resolve.getLinkedGradleProjectPath
import com.intellij.openapi.components.service
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.codeInspection.suppression.GradleSuspendTypecheckingService
import org.jetbrains.plugins.groovy.codeInspection.FileTypeInspectionDisabler

class GradleFileTypeInspectionDisabler : FileTypeInspectionDisabler {

  override fun getDisableableInspections(): Set<Class<out LocalInspectionTool>> = emptySet()

  override fun isTypecheckingDisabled(file: PsiFile): Boolean {
    val linkedProjectPath = file.getLinkedGradleProjectPath() ?: return false
    return file.project.service<GradleSuspendTypecheckingService>().isSuspended(linkedProjectPath)
  }
}
