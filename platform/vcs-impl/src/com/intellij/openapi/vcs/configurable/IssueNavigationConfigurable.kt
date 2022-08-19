// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import javax.swing.JComponent

class IssueNavigationConfigurable(private val myProject: Project) : SearchableConfigurable, NoScroll {
  private var myPanel: IssueNavigationConfigurationPanel? = null
  override fun getDisplayName(): String {
    return VcsBundle.message("configurable.IssueNavigationConfigurationPanel.display.name")
  }

  override fun getHelpTopic(): String {
    return "project.propVCSSupport.Issue.Navigation"
  }

  override fun getId(): String {
    return helpTopic
  }

  override fun createComponent(): JComponent? {
    if (myPanel == null) myPanel = IssueNavigationConfigurationPanel(myProject)
    return myPanel
  }

  override fun disposeUIResources() {
    if (myPanel != null) {
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
    return myPanel != null && myPanel!!.isModified
  }
}