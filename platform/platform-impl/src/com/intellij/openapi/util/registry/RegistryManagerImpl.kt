// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry

import com.intellij.diagnostic.runActivity
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ArrayUtilRt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.util.*

@State(
  name = "Registry",
  storages = [Storage("ide.general.xml", usePathMacroManager = false)],
  useLoadedStateAsExisting = false,
  category = SettingsCategory.SYSTEM,
)
@ApiStatus.Internal
internal class RegistryManagerImpl(coroutineScope: CoroutineScope) : PersistentStateComponent<Element>, RegistryManager {
  private val defaultValueChangeListener = object : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      ApplicationManager.getApplication().messageBus.syncPublisher(RegistryManager.TOPIC).afterValueChanged(value)
    }
  }

  init {
    runActivity("registry keys adding") {
      RegistryKeyBean.addKeysFromPlugins()
    }
    Registry.setValueChangeListener(defaultValueChangeListener)

    // EarlyAccessRegistryManager cannot access AppLifecycleListener
    ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appWillBeClosed(isRestart: Boolean) {
        runCatching {
          EarlyAccessRegistryManager.syncAndFlush()
        }.getOrLogException(logger<RegistryManagerImpl>())
      }
    })

    coroutineScope.coroutineContext.job.invokeOnCompletion {
      Registry.setValueChangeListener(null)
    }
  }

  override fun `is`(key: String): Boolean = Registry._getWithoutStateCheck(key).asBoolean()

  override fun intValue(key: String): Int = Registry._getWithoutStateCheck(key).asInteger()

  override fun stringValue(key: String): @NlsSafe String = Registry._getWithoutStateCheck(key).asString()

  override fun intValue(key: String, defaultValue: Int): Int {
    try {
      return Registry._getWithoutStateCheck(key).asInteger()
    }
    catch (ignore: MissingResourceException) {
      return defaultValue
    }
  }

  override fun get(key: String): RegistryValue = Registry._getWithoutStateCheck(key)

  override fun resetValueChangeListener() {
    Registry.setValueChangeListener(defaultValueChangeListener)
  }

  override fun getState(): Element = Registry.getInstance().getState()

  override fun noStateLoaded() {
    Registry.loadState(state = null, earlyAccess = EarlyAccessRegistryManager.getOrLoadMap())
  }

  override fun loadState(state: Element) {
    log(Registry.loadState(state = state, earlyAccess = EarlyAccessRegistryManager.getOrLoadMap()))
  }

  private fun log(userProperties: Map<String, String>) {
    if (userProperties.size <= (if (userProperties.containsKey("ide.firstStartup")) 1 else 0)) {
      return
    }

    val keys = ArrayUtilRt.toStringArray(userProperties.keys)
    keys.sort()
    val builder = StringBuilder("Registry values changed by user: ")
    for (key in keys) {
      if ("ide.firstStartup" != key) {
        builder.append(key).append(" = ").append(userProperties[key]).append(", ")
      }
    }
    logger<RegistryManager>().info(builder.substring(0, builder.length - 2))
  }

  fun getAll(): List<RegistryValue> = Registry.getAll()

  override suspend fun awaitRegistryLoad() {
    Registry.awaitLoad()
  }
}