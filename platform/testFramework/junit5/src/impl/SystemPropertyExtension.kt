// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.SystemPropertyClassLevel
import org.jetbrains.annotations.TestOnly

@TestOnly
internal class SystemPropertyExtension :
  SystemKeyValueExtensionBase<SystemPropertyExtension.SystemPropertyValueHolder, SystemProperty, SystemPropertyClassLevel>(
    classLevelPropertiesKey,
    SystemProperty::class.java,
    SystemPropertyClassLevel::class.java
  ) {

  data class SystemPropertyValueHolder(val propertyKey: String, val previousValue: String?)

  companion object {
    private val classLevelPropertiesKey: TypedStoreKey<List<SystemPropertyValueHolder>> =
      TypedStoreKey.createKey<List<SystemPropertyValueHolder>>()
  }


  override fun setPropertyValue(annotation: SystemProperty): SystemPropertyValueHolder =
    setPropertyImpl(annotation.propertyKey, annotation.propertyValue)

  override fun setClassPropertyValue(annotation: SystemPropertyClassLevel): SystemPropertyValueHolder =
    setPropertyImpl(annotation.propertyKey, annotation.propertyValue)

  override fun resetPropertyValue(oldValue: SystemPropertyValueHolder) {
    if (oldValue.previousValue == null) {
      System.clearProperty(oldValue.propertyKey)
    }
    else {
      System.setProperty(oldValue.propertyKey, oldValue.previousValue)
    }
  }

  private fun setPropertyImpl(propertyKey: String, propertyValue: String): SystemPropertyValueHolder {
    val previousValue = System.getProperty(propertyKey)
    if (propertyValue.isEmpty()) {
      System.clearProperty(propertyKey)
    }
    else {
      System.setProperty(propertyKey, propertyValue)
    }
    return SystemPropertyValueHolder(propertyKey, previousValue)
  }
}
