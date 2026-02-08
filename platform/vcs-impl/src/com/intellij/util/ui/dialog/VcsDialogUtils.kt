// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.dialog

import com.intellij.ide.plugins.PluginManagerConfigurableUtils.showInstallPluginDialog
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector
import com.intellij.ui.components.ActionLink
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Internal
object VcsDialogUtils {
  @JvmStatic
  @JvmOverloads
  fun getMorePluginsLink(component: Component, beforeShow: (() -> Unit)? = null): ActionLink {
    return ActionLink(VcsBundle.message("more.via.plugins.link")) {
      beforeShow?.invoke()
      VcsStatisticsCollector.MORE_VCS_PLUGINS_LINK_CLICKED.log()
      showInstallPluginDialog(component, Registry.stringValue("vcs.more.plugins.search.query"))
    }
  }
}
