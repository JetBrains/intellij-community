// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import training.lang.LangManager
import training.ui.getFeedbackProposedPropertyName

class ResetOnboardingFeedbackStateAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val langSupport = LangManager.getInstance().getLangSupport() ?: error("Lang support is null for some magic reason")
    val propertyName = getFeedbackProposedPropertyName(langSupport)
    PropertiesComponent.getInstance().setValue(propertyName, false)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}