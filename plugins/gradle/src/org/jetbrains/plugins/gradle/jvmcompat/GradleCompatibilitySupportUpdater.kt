// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus


/**
 * part of GradleJvmSuppportMatrix, extracted for better test-ability
 */
@ApiStatus.Internal
open class GradleCompatibilitySupportUpdater : IdeVersionedDataUpdater<GradleCompatibilityState>(
  GradleJvmSupportMatrix.getInstance()
) {
  override val configUrl: String
    get() = Registry.stringValue("gradle.compatibility.config.url")
  override val updateInterval: Int
    get() = Registry.intValue("gradle.compatibility.update.interval")

  companion object {
    fun getInstance(): GradleCompatibilitySupportUpdater = service()
  }
}