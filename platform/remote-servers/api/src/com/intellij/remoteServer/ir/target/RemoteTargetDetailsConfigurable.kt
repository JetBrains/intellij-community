// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.target

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.remoteServer.ir.config.BaseExtendableConfiguration
import com.intellij.remoteServer.ir.config.BaseExtendableConfiguration.Companion.getTypeImpl
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

internal class RemoteTargetDetailsConfigurable(private val project: Project, private val config: RemoteTargetConfiguration)
  : NamedConfigurable<RemoteTargetConfiguration>(true, null) {

  private val targetConfigurable: Configurable = doCreateConfigurable(config)

  override fun getBannerSlogan(): String = config.displayName

  override fun getIcon(expanded: Boolean): Icon? = config.getTypeImpl().icon

  override fun isModified(): Boolean = targetConfigurable.isModified

  override fun getDisplayName(): String = config.displayName

  override fun apply() = targetConfigurable.apply()

  override fun setDisplayName(name: String) {
    config.displayName = name
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    targetConfigurable.disposeUIResources()
  }

  override fun getEditableObject() = config

  override fun createOptionsPanel(): JComponent {
    val panel = JPanel(
      VerticalLayout(JBUIScale.scale(UIUtil.DEFAULT_VGAP)))
    panel.add(targetConfigurable.createComponent() ?: throw IllegalStateException())

    config.runtimes.resolvedConfigs().forEach {
      val nextConfigurable = doCreateConfigurable(it)
      panel.add(nextConfigurable.createComponent())
    }

    return panel
  }

  private fun doCreateConfigurable(config: BaseExtendableConfiguration) =
    config.getTypeImpl().createConfigurable(project, config)

}