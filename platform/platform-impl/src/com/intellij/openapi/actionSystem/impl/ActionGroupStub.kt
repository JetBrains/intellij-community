// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.actionSystem.*
import java.util.function.Function

internal class ActionGroupStub(override val id: String,
                               @JvmField val actionClass: String,
                               override val plugin: IdeaPluginDescriptor,
                               override val iconPath: String?) : DefaultActionGroup(), ActionStubBase {
  val classLoader: ClassLoader
    get() = plugin.classLoader

  var popupDefinedInXml: Boolean = false

  fun initGroup(target: ActionGroup, actionToId: Function<AnAction, String?>) {
    ActionStub.copyTemplatePresentation(templatePresentation, target.templatePresentation)
    copyActionTextOverrides(target)

    target.shortcutSet = shortcutSet
    val children = childActionsOrStubs
    if (children.isNotEmpty()) {
      if (target !is DefaultActionGroup) {
        throw PluginException("To accept children action group class must extend DefaultActionGroup, got `$actionClass`", plugin.pluginId)
      }
      for (action in children) {
        target.addAction(action, Constraints.LAST, actionToId)
      }
    }
    if (popupDefinedInXml) {
      target.isPopup = isPopup
    }
    target.isSearchable = isSearchable
  }
}
