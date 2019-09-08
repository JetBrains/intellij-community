package com.intellij.remoteServer.ir.configuration

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class RemoteTargetsListConfigurable(private val project: Project) : SearchableConfigurable {
  private val editor = RemoteTargetsMasterDetails(project)

  override fun isModified(): Boolean = editor.isModified

  override fun getId(): String = "Remote.Runners.Configurable"

  override fun getDisplayName(): String = "Remote Targets"

  override fun createComponent(): JComponent = editor.createComponent()

  override fun apply() {
    editor.apply()
  }

  override fun reset() {
    editor.reset()
  }

  override fun disposeUIResources() {
    editor.disposeUIResources()
    super.disposeUIResources()
  }

}