// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.advanced

import com.intellij.BundleBase
import com.intellij.DynamicBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.util.text.StringUtil
import com.intellij.serialization.MutableAccessor
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.text.nullize
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class AdvancedSettingBean : PluginAware, KeyedLazyInstance<AdvancedSettingBean> {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<AdvancedSettingBean> = ExtensionPointName("com.intellij.advancedSetting")
  }

  private var pluginDescriptor: PluginDescriptor? = null

  internal val enumKlass: Class<out Enum<*>>? by lazy {
    @Suppress("UNCHECKED_CAST")
    if (enumClass.isNotBlank())
      (pluginDescriptor?.pluginClassLoader ?: javaClass.classLoader).loadClass(enumClass) as Class<out Enum<*>>
    else
      null
  }

  @Transient
  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  fun getPluginDescriptor(): PluginDescriptor? = this.pluginDescriptor

  /**
   * The ID of the setting.
   * Used to access its value from code; can also be used to search for the setting.
   * If a user changed a registry value with the same ID, the changed value will be migrated to the advanced settings.
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
  var defaultValue: String = ""

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
   * Fully qualified name of the service class which stores the value of the setting.
   * Should be used only when migrating regular settings to advanced settings.
   */
  @Attribute("service")
  @JvmField
  var service: String = ""

  /**
   * Name of the field or property of the class specified in [service] which stores the value of the setting.
   * Should be used only when migrating regular settings to advanced settings.
   */
  @Attribute("property")
  @JvmField
  var property: String = ""

  fun type(): AdvancedSettingType = when {
    enumClass.isNotBlank() -> AdvancedSettingType.Enum
    defaultValue.toIntOrNull() != null -> AdvancedSettingType.Int
    defaultValue == "true" || defaultValue == "false" -> AdvancedSettingType.Bool
    else -> AdvancedSettingType.String
  }

  fun title(): @Nls String = findBundle()?.let { BundleBase.message(it, titleKey.ifEmpty { "advanced.setting.${id}" }) } ?: "!${id}!"

  fun group(): @Nls String? = if (groupKey.isEmpty()) null else findBundle()?.let { BundleBase.message(it, groupKey) }

  fun description(): @Nls String? {
    if (descriptionGetter != null && serviceInstance != null) {
      descriptionGetter!!.invoke(serviceInstance!!)
        ?.let { if ((it as? String)?.isNotEmpty() == true) return it }
    }
    val descriptionKey = descriptionKey.ifEmpty { "advanced.setting.${id}.description" }
    return findBundle()?.takeIf { it.containsKey(descriptionKey) }?.let { BundleBase.message(it, descriptionKey) }
  }

  fun isVisible(): Boolean {
    if (visibilityCheckMethod != null && serviceInstance != null) {
      visibilityCheckMethod!!.invoke(serviceInstance!!)?.let { return it as? Boolean ?: true }
    }
    return true
  }

  fun isEnabled(): Boolean {
    if (enabledCheckMethod != null && serviceInstance != null) {
      enabledCheckMethod!!.invoke(serviceInstance!!)?.let { return it as? Boolean ?: true }
    }
    return true
  }

  fun trailingLabel(): @Nls String? {
    val trailingLabelKey = trailingLabelKey.ifEmpty { "advanced.setting.${id}.trailingLabel" }
    return findBundle()?.takeIf { it.containsKey(trailingLabelKey) }?.let { BundleBase.message(it, trailingLabelKey) }
  }

  fun valueFromString(valueString: String): Any = when (type()) {
    AdvancedSettingType.Int -> valueString.toInt()
    AdvancedSettingType.Bool -> valueString.toBoolean()
    AdvancedSettingType.String -> valueString
    AdvancedSettingType.Enum -> {
      try {
        java.lang.Enum.valueOf(enumKlass!!, valueString)
      }
      catch (_: IllegalArgumentException) {
        java.lang.Enum.valueOf(enumKlass!!, defaultValue)
      }
    }
  }

  fun valueToString(value: Any): String =
    if (type() == AdvancedSettingType.Enum) (value as Enum<*>).name else value.toString()

  val defaultValueObject: Any by lazy { valueFromString(defaultValue) }

  private fun findBundle(): ResourceBundle? {
    val bundleName = bundle.nullize()
                     ?: pluginDescriptor?.takeIf { it.pluginId.idString == "com.intellij" }?.let { ApplicationBundle.BUNDLE }
                     ?: pluginDescriptor?.resourceBundleBaseName
                     ?: return null
    val classLoader = pluginDescriptor?.pluginClassLoader ?: javaClass.classLoader
    return DynamicBundle.getResourceBundle(classLoader, bundleName)
  }

  val serviceInstance: Any? by lazy {
    if (service.isEmpty())
      null
    else {
      val classLoader = pluginDescriptor?.pluginClassLoader ?: javaClass.classLoader
      @Suppress("IncorrectServiceRetrieving")
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

  private val descriptionGetter: Method? by lazy {
    if (property.isEmpty())
      null
    else
      serviceInstance?.let { instance ->
        instance.javaClass.methods.find { it.name == "get" + StringUtil.capitalizeWithJavaBeanConvention(property) + "Description" }
      }
  }

  private val visibilityCheckMethod: Method? by lazy {
    if (property.isEmpty())
      null
    else
      serviceInstance?.let { instance ->
        instance.javaClass.methods.find { it.name == "is" + StringUtil.capitalizeWithJavaBeanConvention(property) + "Visible" }?.takeIf { it.canAccess(instance) }
      }
  }

  private val enabledCheckMethod: Method? by lazy {
    if (property.isEmpty())
      null
    else
      serviceInstance?.let { instance ->
        instance.javaClass.methods.find { it.name == "is" + StringUtil.capitalizeWithJavaBeanConvention(property) + "Enabled" }?.takeIf { it.canAccess(instance) }
      }
  }

  override fun getKey(): String = id

  override fun getInstance(): AdvancedSettingBean = this
}

private val logger = logger<AdvancedSettingsImpl>()

@State(name = "AdvancedSettings", category = SettingsCategory.TOOLS, exportable = true, storages = [
  Storage("advancedSettings.xml", roamingType = RoamingType.DISABLED),
  Storage(value = "ide.general.xml", deprecated = true, roamingType = RoamingType.DISABLED)
])
@ApiStatus.Internal
class AdvancedSettingsImpl : AdvancedSettings(), PersistentStateComponentWithModificationTracker<AdvancedSettingsImpl.AdvancedSettingsState>, Disposable {
  class AdvancedSettingsState {
    var settings: Map<String, String> = mapOf()
  }

  private val epCollector = KeyedExtensionCollector<AdvancedSettingBean, String>(AdvancedSettingBean.EP_NAME.name)
  private val internalState = ConcurrentHashMap<String, Any>()
  private val unknownValues = ConcurrentHashMap<String, String>()
  private val defaultValueCache = ConcurrentHashMap<String, Any>()
  private var modificationCount = 0L

  init {
    AdvancedSettingBean.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<AdvancedSettingBean> {
      override fun extensionAdded(extension: AdvancedSettingBean, pluginDescriptor: PluginDescriptor) {
        logger.info("Extension added ${pluginDescriptor.pluginId}: ${extension.id}")
        val rawValue = unknownValues.remove(extension.id) ?: return
        internalState[extension.id] = extension.valueFromString(rawValue)
      }

      override fun extensionRemoved(extension: AdvancedSettingBean, pluginDescriptor: PluginDescriptor) {
        logger.info("Extension removed ${pluginDescriptor.pluginId}: ${extension.id}")
        defaultValueCache.remove(extension.id)
        val currentValue = internalState.remove(extension.id) ?: return
        unknownValues[extension.id] = extension.valueToString(currentValue)
      }
    }, this)
  }

  override fun dispose() {}

  override fun getState(): AdvancedSettingsState {
    if (logger.isDebugEnabled) {
      logger.debug("Getting advanced settings state: $internalState")
    }
    val retval = AdvancedSettingsState()
    retval.settings = HashMap(unknownValues).apply { putAll(internalState.map { (k, v) -> k to getOption(k).valueToString(v) }.toMap()) }
    return retval
  }

  override fun loadState(state: AdvancedSettingsState) {
    if (logger.isDebugEnabled) {
      logger.debug("Will load advanced settings state: ${state.settings}. Current state: ${this.internalState}")
    }

    this.internalState.clear()

    for ((k, v) in state.settings) {
      val optionOrNull = getOptionOrNull(k)
      if (optionOrNull != null) {
        this.internalState.put(k, optionOrNull.valueFromString(v))
      }
      else {
        unknownValues.put(k, v)
      }
    }
  }

  override fun getStateModificationCount(): Long = modificationCount

  override fun setSetting(id: String, value: Any, expectType: AdvancedSettingType) {
    if (logger.isDebugEnabled) {
      logger.debug("Will set advanced setting $id = $value. Current state: $internalState")
    }
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
    if (logger.isDebugEnabled) {
      logger.debug("Old value for $id: $oldValue")
    }
    if (option.defaultValueObject == value) {
      if (logger.isDebugEnabled) {
        logger.debug("new value \"$value\" for $id is the same as default value, removing the setting")
      }
      internalState.remove(id)
    }
    else {
      internalState.put(id, value)
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
    return internalState[id] ?: defaultValueCache.getOrPut(id) { getOption(id).defaultValueObject }
  }

  override fun getDefault(id: String): Any =
    getOption(id).defaultValueObject

  private fun getOption(id: String): AdvancedSettingBean =
    getOptionOrNull(id) ?: throw IllegalArgumentException("Can't find advanced setting ${id}")

  private fun getOptionOrNull(id: String): AdvancedSettingBean? {
    val bean = epCollector.findSingle(id)
    if (bean == null) {
      logger.info("Cannot find advanced setting $id")
    }
    return bean
  }

  private fun getSettingAndType(id: String): Pair<Any, AdvancedSettingType> =
    getSetting(id) to getOption(id).type()

  fun isNonDefault(id: String): Boolean = internalState.containsKey(id)

  @TestOnly
  fun setSetting(id: String, value: Any, revertOnDispose: Disposable) {
    val (oldValue, type) = getSettingAndType(id)
    setSetting(id, value, type)
    Disposer.register(revertOnDispose, Disposable { setSetting(id, oldValue, type) })
  }
}
