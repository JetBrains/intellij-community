// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.jetbrains.plugins.gradle.properties.models.Property

interface GradleDaemonJvmProperties {
  val version: Property<String>?
  val vendor: Property<String>?
}