package com.intellij.smartUpdate

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

val EP_NAME = ExtensionPointName.create<SmartUpdateStep>("com.intellij.smartUpdateStep")

interface SmartUpdateStep {
  val id: @NonNls String
  val stepName: @Nls String
  fun performUpdateStep(project: Project, e: AnActionEvent? = null, onSuccess: () -> Unit)
  fun isAvailable(project: Project): Boolean = true
  fun getDetailsComponent(project: Project): JComponent? = null
}