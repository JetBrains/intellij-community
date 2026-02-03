// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.jetbrains.plugins.gradle.properties.models.Property
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.util.toJvmVendor

data class GradleDaemonJvmPropertiesImpl(
  override val version: Property<String>?,
  override val vendor: Property<String>?,
) : GradleDaemonJvmProperties {

  override val criteria: GradleDaemonJvmCriteria
    get() = GradleDaemonJvmCriteria(
      version = version?.value,
      vendor = vendor?.value?.toJvmVendor()
    )
}
