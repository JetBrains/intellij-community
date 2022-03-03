// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.advanced

import com.intellij.BundleBase
import com.intellij.DynamicBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.extensions.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.serialization.MutableAccessor
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.text.nullize
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.util.*

class AdvancedSettingBean : PluginAware, KeyedLazyInstance<AdvancedSettingBean> {
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

  /**
   * The ID of the setting. Used to access its value from code; can also be used to search for the setting. If a registry value with the
   * same ID was changed by the user, the changed value will be migrated to the advanced settings.
   */
  @Attribute("id")
  @RequiredElement
  @JvmField
  var id: String = ""

  /**
   * The default value of the setting. Also determines the type of control used to display the setting. If [enumClass] is specified,
   * the setting is shown as a combobox. If the default value is `true` or `false`, the setting is shown as a checkbox. Otherwise, the
   * setting is shown as a text field, and if the default value is an integer, only integers will be accepted as property values.
   */
  @Attribute("default")
  @RequiredElement
  @JvmField
  var defaultValue = ""

  /**
   * Name of the property in the resource bundle [bundle] which holds the label for the setting displayed in the UI.
   * If not specified, the default is `advanced.setting.<id>`
   */
  @Attribute("titleKey")
  @JvmField
  var titleKey: String = ""

  /**
   * Name of the property in the resource bundle [bundle] which holds the name of the group in which the setting is displayed in the UI.
   * If not specified, the setting will be shown in the "Other" group.
   */
  @Attribute("groupKey")
  @JvmField
  var groupKey: String = ""

  /**
   * Name of the property in the resource bundle [bundle] which holds the comment displayed in the UI underneath the control for editing
   * the property. If not specified, the default is `advanced.setting.<id>.description`.
   */
  @Attribute("descriptionKey")
  @JvmField
  var descriptionKey: String = ""

  /**
   * Name of the property in the resource bundle [bundle] which holds the trailing label displayed in the UI to the right of the
   * control for editing the setting value. If not specified, the default is `advanced.setting.<id>.trailingLabel`.
   */
  @Attribute("trailingLabelKey")
  @JvmField
  var trailingLabelKey: String = ""

  /**
   * The resource bundle containing the label and other UI text for this option. If not specified, [ApplicationBundle] is used for settings
   * declared in the platform, and the plugin resource bundle is used for settings defined in plugins.
   */
  @Attribute("bundle")
  @JvmField
  var bundle: String = ""

  @Attribute("enumClass")
  @JvmField
  var enumClass: String = ""

  /**
   * Fully-qualified name of the service class which stores the value of the setting. Should be used only when migrating regular
   * settings to advanced settings.
   */
  @Attribute("service")
  @JvmField
  var service: String = ""

  /**
   * Name of the field or property of the class specified in [service] which stores the value of the setting. Should be used only when
   * migrating regular settings to advanced settings.
   */
  @Attribute("property")
  @JvmField
  var property: String = ""

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
    return findBundle()?.let { BundleBase.message(it, titleKey.ifEmpty { "advanced.setting.$id" }) } ?: "!$id!"
  }

  @Nls
  fun group(): String? {
    if (groupKey.isEmpty()) return null
    return findBundle()?.let { BundleBase.message(it, groupKey) }
  }

  @Nls
  fun description(): String? {
    val descriptionKey = descriptionKey.ifEmpty { "advanced.setting.$id.description" }
    return findBundle()?.takeIf { it.containsKey(descriptionKey) }?.let { BundleBase.message(it, descriptionKey) }
  }

  @Nls
  fun trailingLabel(): String? {
    val trailingLabelKey = trailingLabelKey.ifEmpty { "advanced.setting.$id.trailingLabel" }
    return findBundle()?.takeIf { it.containsKey(trailingLabelKey) }?.let { BundleBase.message(it, trailingLabelKey) }
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

  val serviceInstance: Any? by lazy {
    if (service.isEmpty())
      null
    else {
      val classLoader = pluginDescriptor?.pluginClassLoader ?: javaClass.classLoader
      ApplicationManager.getApplication().getService(classLoader.loadClass(service))
    }
  }

  val accessor: MutableAccessor? by lazy {
    if (property.isEmpty())
      null
    else
      serviceInstance?.let { instance ->
        XmlSerializerUtil.getAccessors(instance.javaClass).find { it.name == property }
      }
  }

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName<AdvancedSettingBean>("com.intellij.advancedSetting")
  }

  override fun getKey(): String = id

  override fun getInstance(): AdvancedSettingBean = this
}

@State(name = "AdvancedSettings", storages = [Storage("advancedSettings.xml"), Storage(value = "ide.general.xml", deprecated = true)])
class AdvancedSettingsImpl : AdvancedSettings(), PersistentStateComponentWithModificationTracker<AdvancedSettingsImpl.AdvancedSettingsState>, Disposable {
  class AdvancedSettingsState {
    var settings = mutableMapOf<String, String>()
  }

  private val epCollector = KeyedExtensionCollector<AdvancedSettingBean, String>(AdvancedSettingBean.EP_NAME.name)
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
    val instance = option.serviceInstance
    if (instance != null) {
      option.accessor?.let {
        it.set(instance, value)
        return
      }
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
    val option = getOption(id)
    val instance = option.serviceInstance
    if (instance != null) {
      option.accessor?.let {
        return it.read(instance)
      }
    }
    return state.get(id) ?: defaultValueCache.getOrPut(id) { getOption(id).defaultValueObject }
  }

  override fun getDefault(id: String): Any {
    return getOption(id).defaultValueObject
  }

  private fun getOption(id: String): AdvancedSettingBean {
    return getOptionOrNull(id) ?: throw IllegalArgumentException("Can't find advanced setting $id")
  }

  private fun getOptionOrNull(id: String): AdvancedSettingBean? = epCollector.findSingle(id)

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
