// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.launch

/**
 * Forces JCEF early startup in order to support co-existence with JavaFX (see IDEA-236310).
 */
private class JBCefStartup {
  @Suppress("unused")
  private var STARTUP_CLIENT: JBCefClient? = null // auto-disposed along with JBCefApp on IDE shutdown

  // os=mac
  init {
    val app = ApplicationManager.getApplication()
    if (!app.isUnitTestMode) {
      app.coroutineScope.launch {
        doInit()
      }
    }
  }

  private suspend fun doInit() {
    @Suppress("SpellCheckingInspection") val isPreinit = (ApplicationManager.getApplication() as ComponentManagerEx)
      .getServiceAsync(RegistryManager::class.java)
      .await()
      .get("ide.browser.jcef.preinit")
    if (isPreinit.asBoolean() && JBCefApp.isSupported()) {
      try {
        STARTUP_CLIENT = JBCefApp.getInstance().createClient()
      }
      catch (ignore: IllegalStateException) {
      }
    }
    else {
      //todo[tav] remove when JavaFX + JCEF co-exist is fixed on macOS, or when JavaFX is deprecated
      //This code enables pre initialization of JCEF on macOS if and only if JavaFX Runtime plugin is installed
      val id = "com.intellij.javafx"
      val javaFX = PluginId.findId(id)
      if (javaFX == null || PluginManager.getInstance().findEnabledPlugin(javaFX) == null) {
        ApplicationManager.getApplication().messageBus.connect()
          .subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
            override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
              if (pluginDescriptor.pluginId.idString == id) {
                isPreinit.setValue(true)
              }
            }
          })
      }
    }
  }
}