// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.advanced

import com.intellij.DynamicBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.extensions.*
import com.intellij.openapi.util.Disposer
import com.intellij.util.text.nullize
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.util.*

class AdvancedSettingBean : PluginAware {
  private var pluginDescriptor: PluginDescriptor? = null

  val enumKlass: Class<Enum<*>>? by lazy {
    @Suppress("UNCHECKED_CAST")
    if (enumClass.isNotBlank())
      (pluginDescriptor?.pluginClassLoader ?: javaClass.classLoader).loadClass(enumClass) as Class<Enum<*>>
    else
      null
  }

  @Transient
  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  fun getPluginDescriptor(): PluginDescriptor? {
    return this.pluginDescriptor
  }

  @Attribute("id")
  @RequiredElement
  @JvmField
  var id: String = ""

  @Attribute("default")
  @RequiredElement
  @JvmField
  var defaultValue = ""

  @Attribute("titleKey")
  @JvmField
  var titleKey: String = ""

  @Attribute("groupKey")
  @JvmField
  var groupKey: String = ""

  @Attribute("descriptionKey")
  @JvmField
  var descriptionKey: String = ""

  @Attribute("trailingLabelKey")
  @JvmField
  var trailingLabelKey: String = ""

  @Attribute("bundle")
  @JvmField
  var bundle: String = ""

  @Attribute("enumClass")
  @JvmField
  var enumClass: String = ""

  fun type(): AdvancedSettingType {
    return when {
      enumClass.isNotBlank() -> AdvancedSettingType.Enum
      defaultValue.toIntOrNull() != null -> AdvancedSettingType.Int
      defaultValue == "true" || defaultValue == "false" -> AdvancedSettingType.Bool
      else -> AdvancedSettingType.String
    }
  }

  @Nls
  fun title(): String {
    return findBundle()?.getString(titleKey.ifEmpty { "advanced.setting.$id" }) ?: "!$id!"
  }

  @Nls
  fun group(): String? {
    if (groupKey.isEmpty()) return null
    return findBundle()?.getString(groupKey)
  }

  @Nls
  fun description(): String? {
    val descriptionKey = descriptionKey.ifEmpty { "advanced.setting.$id.description" }
    return findBundle()?.takeIf { it.containsKey(descriptionKey) }?.getString(descriptionKey)
  }

  @Nls
  fun trailingLabel(): String? {
    val trailingLabelKey = trailingLabelKey.ifEmpty { "advanced.setting.$id.trailingLabel" }
    return findBundle()?.takeIf { it.containsKey(trailingLabelKey) }?.getString(trailingLabelKey)
  }

  fun valueFromString(valueString: String): Any {
    return when (type()) {
      AdvancedSettingType.Int -> valueString.toInt()
      AdvancedSettingType.Bool -> valueString.toBoolean()
      AdvancedSettingType.String -> valueString
      AdvancedSettingType.Enum -> {
        try {
          java.lang.Enum.valueOf(enumKlass!!, valueString)
        }
        catch (e: IllegalArgumentException) {
          java.lang.Enum.valueOf(enumKlass!!, defaultValue)
        }
      }
    }
  }

  fun valueToString(value: Any): String {
    return if (type() == AdvancedSettingType.Enum) (value as Enum<*>).name else value.toString()
  }

  val defaultValueObject by lazy { valueFromString(defaultValue) }

  private fun findBundle(): ResourceBundle? {
    val bundleName = bundle.nullize()
                     ?: pluginDescriptor?.takeIf { it.pluginId.idString == "com.intellij" } ?.let { ApplicationBundle.BUNDLE }
                     ?: pluginDescriptor?.resourceBundleBaseName
                     ?: return null
    val classLoader = pluginDescriptor?.pluginClassLoader ?: javaClass.classLoader
    return DynamicBundle.INSTANCE.getResourceBundle(bundleName, classLoader)
  }

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName<AdvancedSettingBean>("com.intellij.advancedSetting")
  }
}

@State(name = "AdvancedSettings", storages = [Storage("advancedSettings.xml"), Storage(value = "ide.general.xml", deprecated = true)])
class AdvancedSettingsImpl : AdvancedSettings(), PersistentStateComponentWithModificationTracker<AdvancedSettingsImpl.AdvancedSettingsState>, Disposable {
  class AdvancedSettingsState {
    var settings = mutableMapOf<String, String>()
  }

  private var state = mutableMapOf<String, Any>()
  private var defaultValueCache = mutableMapOf<String, Any>()
  private var modificationCount = 0L

  init {
    AdvancedSettingBean.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<AdvancedSettingBean?> {
      override fun extensionRemoved(extension: AdvancedSettingBean, pluginDescriptor: PluginDescriptor) {
        defaultValueCache.remove(extension.id)
      }
    }, this)
  }

  override fun dispose() {
  }

  override fun getState(): AdvancedSettingsState {
    return AdvancedSettingsState().also { state.map { (k, v) -> k to getOption(k).valueToString(v) }.toMap(it.settings) }
  }

  override fun loadState(state: AdvancedSettingsState) {
    this.state.clear()
    state.settings.mapNotNull { (k, v) -> getOptionOrNull(k)?.let { option -> k to option.valueFromString(v) } }.toMap(this.state)
  }

  override fun getStateModificationCount() = modificationCount

  override fun setSetting(id: String, value: Any, expectType: AdvancedSettingType) {
    val option = getOption(id)
    if (option.type() != expectType) {
      throw IllegalArgumentException("Setting type ${option.type()} does not match parameter type $expectType")
    }

    val oldValue = getSetting(id)
    if (option.defaultValueObject == value) {
      state.remove(id)
    }
    else {
      state.put(id, value)
    }
    modificationCount++

    ApplicationManager.getApplication().messageBus.syncPublisher(AdvancedSettingsChangeListener.TOPIC)
      .advancedSettingChanged(id, oldValue, value)
  }

  override fun getSetting(id: String): Any {
    return state.get(id) ?: defaultValueCache.getOrPut(id) { getOption(id).defaultValueObject }
  }

  private fun getOption(id: String): AdvancedSettingBean {
    return getOptionOrNull(id) ?: throw IllegalArgumentException("Can't find advanced setting $id")
  }

  private fun getOptionOrNull(id: String) = AdvancedSettingBean.EP_NAME.findFirstSafe { it.id == id }

  private fun getSettingAndType(id: String): Pair<Any, AdvancedSettingType> {
    val option = getOption(id)
    return getSetting(id) to option.type()
  }

  fun isNonDefault(id: String): Boolean {
    return id in state
  }

  @TestOnly
  fun setSetting(id: String, value: Any, revertOnDispose: Disposable) {
    val (oldValue, type) = getSettingAndType(id)
    setSetting(id, value, type)
    Disposer.register(revertOnDispose, Disposable { setSetting(id, oldValue, type )})
  }
}
