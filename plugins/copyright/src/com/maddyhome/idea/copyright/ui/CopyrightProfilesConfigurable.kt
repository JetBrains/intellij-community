// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.maddyhome.idea.copyright.ui

import com.intellij.copyright.CopyrightBundle
import com.intellij.openapi.options.MasterDetails
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DetailsComponent
import javax.swing.JComponent

private const val ID = "copyright.profiles"

/**
 * The "Profiles" child of [CopyrightProjectConfigurable].
 *
 * It creates [CopyrightProfilesPanel] only when the UI needs it.
 * The Settings tree build then creates no Swing component for this page.
 */
internal class CopyrightProfilesConfigurable(private val project: Project) : SearchableConfigurable, MasterDetails {
  private var panel: CopyrightProfilesPanel? = null

  fun getOrCreatePanel(): CopyrightProfilesPanel = panel ?: CopyrightProfilesPanel(project).also { panel = it }

  override fun getId(): String = ID

  override fun getDisplayName(): String = CopyrightBundle.message("configurable.CopyrightProfilesPanel.display.name")

  override fun getHelpTopic(): String = ID

  override fun createComponent(): JComponent = getOrCreatePanel().createComponent()

  override fun getPreferredFocusedComponent(): JComponent? = panel?.preferredFocusedComponent

  override fun isModified(): Boolean = panel?.isModified == true

  override fun apply() {
    panel?.apply()
  }

  override fun reset() {
    panel?.reset()
  }

  override fun cancel() {
    panel?.cancel()
  }

  override fun disposeUIResources() {
    panel?.disposeUIResources()
  }

  override fun initUi() {
    getOrCreatePanel().initUi()
  }

  override fun getToolbar(): JComponent = getOrCreatePanel().toolbar

  override fun getMaster(): JComponent = getOrCreatePanel().master

  override fun getDetails(): DetailsComponent = getOrCreatePanel().details
}
