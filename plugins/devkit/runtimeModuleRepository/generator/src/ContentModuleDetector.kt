// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.generator

import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import org.jetbrains.jps.model.module.JpsModule

/**
 * Provides a way to detect if a [JpsModule] is registered as a content module and obtain the registration data.
 * This is needed to generate proper information about the module in the runtime module repository.
 */
interface ContentModuleDetector {
  fun findContentModuleData(jpsModule: JpsModule): ContentModuleRegistrationData?
}

data class ContentModuleRegistrationData(
  val name: String,
  val namespace: String,
  val visibility: RuntimeModuleVisibility,
)

/**
 * This implementation is temporarily used in the places where information about content modules is not available, when running the
 * IDE or tests from sources without a dev build.
 */
object NoContentModuleDetector : ContentModuleDetector {
  override fun findContentModuleData(jpsModule: JpsModule): ContentModuleRegistrationData? = null
}