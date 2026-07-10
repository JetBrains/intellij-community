// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.CompositeConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor

internal class ResetSettingsNewBadgesAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val props = PropertiesComponent.getInstance()
    val ids = mutableSetOf<String>()
    collectIds(ConfigurableExtensionPointUtil.getConfigurableGroup(e.project, true).configurables, ids)
    for (id in ids) {
      props.unsetValue(SettingsNewBadgeRecorder.KEY_PREFIX + id)
    }
  }

  private fun collectIds(configurables: Array<out UnnamedConfigurable>, result: MutableSet<String>) {
    for (configurable in configurables) {
      if (configurable is Configurable) {
        result += ConfigurableVisitor.getId(configurable)
      }
      val children: Array<out UnnamedConfigurable> = when (configurable) {
        is Configurable.Composite -> configurable.configurables
        is CompositeConfigurable<*> -> configurable.configurables.toTypedArray()
        else -> emptyArray()
      }
      if (children.isNotEmpty()) {
        collectIds(children, result)
      }
    }
  }
}
