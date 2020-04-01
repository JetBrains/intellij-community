package com.intellij.grazie.ide.inspection.detection.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GraziePlugin
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

class GrazieGoToSettingsQuickFix : LocalQuickFix {
  override fun getFamilyName() = GrazieBundle.message("grazie.detection.quickfix.go-to-settings.family")

  override fun getName() = GrazieBundle.message("grazie.detection.quickfix.go-to-settings.text")

  override fun startInWriteAction() = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    ShowSettingsUtil.getInstance().showSettingsDialog(project, GraziePlugin.group)
  }
}