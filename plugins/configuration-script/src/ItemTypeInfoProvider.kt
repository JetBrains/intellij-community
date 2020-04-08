package com.intellij.configurationScript

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.BeanBinding
import java.lang.reflect.ParameterizedType

internal class ItemTypeInfoProvider(private val hostClass: Class<out BaseState>) {
  private val accessors by lazy(LazyThreadSafetyMode.NONE) {
    BeanBinding.getAccessors(hostClass)
  }

  fun getListItemType(propertyName: String, logAsErrorIfPropertyNotFound: Boolean): Class<out BaseState>? {
    val accessor = accessors.find { it.name == propertyName }
    if (accessor == null) {
      val message = "Property not found (name=$propertyName, hostClass=${hostClass.name})"
      if (logAsErrorIfPropertyNotFound) {
        LOG.error(message)
      }
      else {
        LOG.warn(message)
      }
      return null
    }

    val type = accessor.genericType
    if (type !is ParameterizedType) {
      LOG.error("$type not supported (name=$propertyName, hostClass=${hostClass.name})")
      return null
    }

    val actualTypeArguments = type.actualTypeArguments
    LOG.assertTrue(actualTypeArguments.size == 1)
    @Suppress("UNCHECKED_CAST")
    return actualTypeArguments[0] as Class<out BaseState>
  }
}