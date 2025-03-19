// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.diagnostic.logger
import org.gradle.api.logging.LogLevel
import org.jetbrains.plugins.gradle.properties.models.Property
import java.util.Locale

private val LOG = logger<GradlePropertiesImpl>()

data class GradlePropertiesImpl(
  override val javaHomeProperty: Property<String>?,
  override val logLevel: Property<String>?,
  override val parallel: Property<Boolean>?,
  override val isolatedProjects: Property<Boolean>?,
  override val jvmOptions: Property<String>?,
) : GradleProperties {

  override fun getGradleLogLevel(): LogLevel? {
    if (logLevel == null) {
      return null
    }
    var value = logLevel.value
    var name = value.uppercase(Locale.ROOT)
    try {
      return LogLevel.valueOf(name)
    }
    catch (_: IllegalArgumentException) {
      LOG.warn("The Gradle property 'org.gradle.logging.level=$value' is invalid. It must be one of quiet, warn, lifecycle, info, or debug")
      return null
    }
  }
}