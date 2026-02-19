// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef.menu

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun isJcefContextMenuActionGroupRegistered(): Boolean {
  val registryEnabled = Registry.`is`("remoteDev.jcef.prefer.internal.context.menu.actions", false)
  val actionGroupRegistered = ActionUtil.getActionGroup("Jcef.ContextMenuGroup") != null
  fileLogger().debug("JCEF context menu action group availability check: registryEnabled=$registryEnabled, actionGroupRegistered=$actionGroupRegistered")
  return registryEnabled && actionGroupRegistered
}