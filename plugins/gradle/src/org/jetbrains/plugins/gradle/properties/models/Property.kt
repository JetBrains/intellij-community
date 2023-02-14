// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties.models

/**
 * Represents property in the properties file that is described by [location]
 */
data class Property<T>(
  val value: T,
  val location: String
)