// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.jetbrains.plugins.gradle.properties.models.Property

data class GradlePropertiesImpl(
  override val javaHomeProperty: Property<String>?,
  override val gradleLoggingLevel: Property<String>?,
  override val parallel: Property<Boolean>?
) : GradleProperties