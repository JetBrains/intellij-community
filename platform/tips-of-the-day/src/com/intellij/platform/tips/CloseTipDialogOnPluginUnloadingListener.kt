// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tips

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.components.serviceIfCreated

private class CloseTipDialogOnPluginUnloadingListener : DynamicPluginListener {
  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    serviceIfCreated<TipAndTrickManager>()?.closeTipDialog()
  }
}
