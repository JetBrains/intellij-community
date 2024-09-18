// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.openapi.util.registry.Registry
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Sets the [value] for the Registry [key], runs, and reverts to previous value.
 *
 * Boolean keys are supported currently.
 * TODO change the [value] type to [String] when needed.
 *
 * @see com.intellij.openapi.util.registry.RegistryValue.setValue
 */
class RegistryKeyExtension(
  key: String,
  private val value: Boolean,
) : BeforeEachCallback,
    AfterEachCallback {

  private val registryValue = Registry.get(key)
  private var previous: Boolean = false

  override fun beforeEach(context: ExtensionContext?) {
    previous = registryValue.asBoolean()
    registryValue.setValue(value)
  }

  override fun afterEach(context: ExtensionContext?) {
    registryValue.setValue(previous)
  }
}
