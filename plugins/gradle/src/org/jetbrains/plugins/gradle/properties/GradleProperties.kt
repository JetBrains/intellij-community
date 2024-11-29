// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.gradle.api.logging.LogLevel
import org.jetbrains.plugins.gradle.properties.models.Property

interface GradleProperties  {

  val javaHomeProperty: Property<String>?

  val logLevel: Property<String>?
  fun getGradleLogLevel(): LogLevel?

  val parallel: Property<Boolean>?
  val isolatedProjects: Property<Boolean>?

  val jvmOptions: Property<String>?
}