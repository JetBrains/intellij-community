// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry

import com.intellij.application.options.RegistryManager
import com.intellij.diagnostic.runActivity
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ArrayUtilRt
import com.intellij.util.EarlyAccessRegistryManager
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.util.*

@State(name = "Registry", storages = [Storage("ide.general.xml")], useLoadedStateAsExisting = false, category = SettingsCategory.SYSTEM)
@ApiStatus.Internal
internal class RegistryManagerImpl : PersistentStateComponent<Element>, RegistryManager, Disposable {
  init {
    runActivity("registry keys adding") {
      RegistryKeyBean.addKeysFromPlugins()
    }
    Registry.setValueChangeListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        ApplicationManager.getApplication().messageBus.syncPublisher(RegistryManager.TOPIC).afterValueChanged(value)
      }
    })

    // EarlyAccessRegistryManager cannot access AppLifecycleListener
    ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appWillBeClosed(isRestart: Boolean) {
        try {
          EarlyAccessRegistryManager.syncAndFlush()
        }
        catch (e: Throwable) {
          Logger.getInstance(RegistryManagerImpl::class.java).error(e)
        }
      }
    })
  }

  override fun dispose() {
    Registry.setValueChangeListener(null)
  }

  override fun `is`(key: String): Boolean {
    return Registry._getWithoutStateCheck(key).asBoolean()
  }

  override fun intValue(key: String) = Registry._getWithoutStateCheck(key).asInteger()

  override fun stringValue(key: String) = Registry._getWithoutStateCheck(key).asString()

  override fun intValue(key: String, defaultValue: Int): Int {
    return try {
      Registry._getWithoutStateCheck(key).asInteger()
    }
    catch (ignore: MissingResourceException) {
      defaultValue
    }
  }

  override fun get(key: String) = Registry._getWithoutStateCheck(key)

  override fun getState() = Registry.getInstance().state

  override fun noStateLoaded() {
    Registry.markAsLoaded()
  }

  override fun loadState(state: Element) {
    log(Registry.loadState(state) ?: return)
  }

  private fun log(userProperties: Map<String, String>) {
    if (userProperties.size <= (if (userProperties.containsKey("ide.firstStartup")) 1 else 0)) {
      return
    }

    val keys = ArrayUtilRt.toStringArray(userProperties.keys)
    Arrays.sort(keys)
    val builder = StringBuilder("Registry values changed by user: ")
    for (key in keys) {
      if ("ide.firstStartup" == key) {
        continue
      }
      builder.append(key).append(" = ").append(userProperties[key]).append(", ")
    }
    logger<RegistryManager>().info(builder.substring(0, builder.length - 2))
  }

  fun getAll(): List<RegistryValue> {
    return Registry.getAll()
  }
}