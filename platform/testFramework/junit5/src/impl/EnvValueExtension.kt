// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.common.replaceEnvLoader
import com.intellij.testFramework.junit5.EnvValue
import com.intellij.testFramework.junit5.EnvValueClassLevel
import com.intellij.util.EnvironmentUtil
import org.jetbrains.annotations.TestOnly

@TestOnly
internal class EnvValueExtension :
  SystemKeyValueExtensionBase<Map<String, String>, EnvValue, EnvValueClassLevel>(
    classLevelPropertiesKey,
    EnvValue::class.java,
    EnvValueClassLevel::class.java
  ) {


  companion object {
    private val classLevelPropertiesKey: TypedStoreKey<List<Map<String, String>>> =
      TypedStoreKey.createKey<List<Map<String, String>>>()
  }

  override fun setPropertyValue(annotation: EnvValue): Map<String, String> = setPropertyValueImpl(annotation.envName, annotation.value)

  override fun setClassPropertyValue(annotation: EnvValueClassLevel): Map<String, String> =
    setPropertyValueImpl(annotation.envName, annotation.value)

  override fun resetPropertyValue(oldValue: Map<String, String>) {
    EnvironmentUtil.setEnvironmentLoader { oldValue }
  }

  private fun setPropertyValueImpl(env: String, value: String): Map<String, String> {
    val current = EnvironmentUtil.getEnvironmentMap()
    replaceEnvLoader(mapOf(env to value), current)
    return current
  }
}
