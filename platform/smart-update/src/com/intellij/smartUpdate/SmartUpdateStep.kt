package com.intellij.smartUpdate

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

val EP_NAME = ExtensionPointName.create<SmartUpdateStep>("smart.update.step")

interface SmartUpdateStep {
  val id: String
  fun performUpdateStep(project: Project, e: AnActionEvent? = null, onSuccess: () -> Unit)
  fun isAvailable(): Boolean = true
}