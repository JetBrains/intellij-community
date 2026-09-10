// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.annotations.ApiStatus

abstract class CompositeConfigurable<T : UnnamedConfigurable> : BaseConfigurable() {

  private val lazyConfigurables = SynchronizedClearableLazy { createConfigurables() }

  open val configurables: List<T>
    get() = lazyConfigurables.value

  /** Kotlin-only alias for [configurables]. It keeps the call form of the former Java method. */
  @JvmSynthetic
  @JvmName("getConfigurablesKotlin")
  @Deprecated("Use configurables property instead", ReplaceWith("configurables"))
  @ApiStatus.ScheduledForRemoval
  fun getConfigurables(): List<T> = configurables

  override fun reset() {
    for (configurable in configurables) {
      configurable.reset()
    }
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    for (configurable in configurables) {
      configurable.apply()
    }
  }

  override fun isModified(): Boolean {
    return configurables.any { it.isModified }
  }

  override fun disposeUIResources() {
    if (lazyConfigurables.isInitialized()) {
      for (configurable in configurables) {
        configurable.disposeUIResources()
      }
      lazyConfigurables.drop()
    }
  }

  protected abstract fun createConfigurables(): List<T>
}

@ApiStatus.Internal
fun getConfigurableTitle(configurable: UnnamedConfigurable): String? {
  if (configurable is BeanConfigurable<*>) {
    return configurable.title
  }
  if (configurable is BoundConfigurable) {
    return configurable.displayName
  }
  return null
}
