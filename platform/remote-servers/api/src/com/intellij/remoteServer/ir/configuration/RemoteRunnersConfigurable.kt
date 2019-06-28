package com.intellij.remoteServer.ir.configuration

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.remoteServer.ir.target.getAdaptedRemoteRunnersConfigurables
import javax.swing.JComponent

class RemoteRunnersConfigurable(private val project: Project) : SearchableConfigurable,
                                                                Configurable.Composite,
                                                                Configurable.NoScroll,
                                                                Configurable.NoMargin {
  private val editor = RemoteRunnersEditor(project)

  override fun isModified(): Boolean = editor.isModified

  override fun getId(): String = "Remote.Runners.Configurable"

  override fun getDisplayName(): String = "Runners"

  override fun createComponent(): JComponent = editor.createComponent()

  override fun apply() {
    editor.apply()
  }

  override fun reset() {
    editor.reset()
  }

  override fun getConfigurables(): Array<Configurable> = getAdaptedRemoteRunnersConfigurables(project).toTypedArray()
}