// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.WelcomeScreenTab
import com.intellij.openapi.wm.WelcomeTabFactory
import com.intellij.ui.components.JBList

//Service for sharing the state and objects between Welcome Screen tabs
@Service
class WelcomeScreenService: Disposable {

  private val factory2tab = mutableMapOf<Class<out WelcomeTabFactory>, WelcomeScreenTab>()
  private var tabs: JBList<WelcomeScreenTab>? = null
  var pluginManagerConfigurable: PluginManagerConfigurable? = null

  fun selectTabByType(tabFactoryType: Class<out WelcomeTabFactory>) {
    val pluginsTab = factory2tab[tabFactoryType]
    if (pluginsTab != null) {
      tabs?.setSelectedValue(pluginsTab, true)
    }
  }

  fun registerTabs(wsTabs: JBList<WelcomeScreenTab>) {
    tabs = wsTabs
  }

  fun registerFactoryToTab(factory: Class<out WelcomeTabFactory>, tab: WelcomeScreenTab) {
    factory2tab[factory] = tab
  }

  override fun dispose() {
    pluginManagerConfigurable = null
    factory2tab.clear()
    tabs = null
  }

  companion object {
    @JvmStatic
    fun getInstance(): WelcomeScreenService {
      return service()
    }
  }

}