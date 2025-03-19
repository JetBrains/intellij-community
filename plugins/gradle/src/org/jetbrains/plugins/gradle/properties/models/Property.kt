// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties.models

import java.nio.file.Path
import java.util.*

/**
 * Represents property in the properties file that is described by [location]
 */
data class Property<T>(
  val value: T,
  val location: Path
)

fun Properties.getStringProperty(key: String, propertiesPath: Path): Property<String>? {
  val property = getProperty(key) ?: return null
  return Property(property, propertiesPath)
}

fun Properties.getBooleanProperty(key: String, propertiesPath: Path): Property<Boolean>? {
  val property = getProperty(key) ?: return null
  return Property(property.toBoolean(), propertiesPath)
}
