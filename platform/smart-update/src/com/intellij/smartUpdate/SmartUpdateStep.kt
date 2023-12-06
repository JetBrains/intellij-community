package com.intellij.smartUpdate

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.ui.layout.ComponentPredicate
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

val EP_NAME = ExtensionPointName.create<SmartUpdateStep>("com.intellij.smartUpdateStep")

interface SmartUpdateStep {
  val id: @NonNls String
  val stepName: @Nls String

  /**
   * Perform update step and proceed by invoking onSuccess()
   *
   * @param e null if task is invoked after restart or by scheduler, otherwise user-initiated
   * @param onSuccess must be called to proceed to next step
   */
  fun performUpdateStep(project: Project, e: AnActionEvent? = null, onSuccess: () -> Unit)
  fun isAvailable(project: Project): Boolean = true
  fun getDetailsComponent(project: Project): JComponent? = null
  fun detailsVisible(project: Project): ComponentPredicate = ComponentPredicate.TRUE
}

interface StepOption: SmartUpdateStep {
  val optionName: @Nls String
  val groupName: @Nls String
}