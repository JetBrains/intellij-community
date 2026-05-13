// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.uiSettingsListener.foo

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.testFramework.plugins.PluginTestHandle
import com.intellij.util.application

@Service
internal class MyFooUISettingsListenerService: PluginTestHandle<(UISettings) -> Unit, Unit> {
  var callback: ((UISettings) -> Unit)? = null
  override fun test(arg: (UISettings) -> Unit) {
    callback = arg
  }
}

internal class MyFooUISettingsListener : UISettingsListener {
  override fun uiSettingsChanged(uiSettings: UISettings) {
    application.service<MyFooUISettingsListenerService>().callback?.invoke(uiSettings)
  }
}
