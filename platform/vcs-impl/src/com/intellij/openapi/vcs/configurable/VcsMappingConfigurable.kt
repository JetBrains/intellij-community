// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

@ApiStatus.Internal
class VcsMappingConfigurable(private val myProject: Project) : SearchableConfigurable {

  companion object {
    private const val ID = "project.propVCSSupport.DirectoryMappings"
    const val HELP_ID: String = "project.propVCSSupport.Mappings"
  }

  private var myPanel: VcsDirectoryConfigurationPanel? = null

  @Nls
  override fun getDisplayName(): @Nls String {
    return VcsBundle.message("configurable.VcsDirectoryConfigurationPanel.display.name")
  }

  override fun getId(): String {
    return ID
  }

  override fun getHelpTopic(): String {
    return HELP_ID
  }

  override fun createComponent(): JComponent? {
    if (myPanel == null) myPanel = VcsDirectoryConfigurationPanel(myProject)
    return myPanel
  }

  override fun disposeUIResources() {
    if (myPanel != null) {
      Disposer.dispose(myPanel!!)
      myPanel = null
    }
  }

  override fun reset() {
    if (myPanel != null) {
      myPanel!!.reset()
    }
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    if (myPanel != null) {
      myPanel!!.apply()
    }
  }

  override fun isModified(): Boolean {
    return myPanel != null && myPanel!!.isModified()
  }
}
