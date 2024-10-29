// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.openapi.util.registry.Registry
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
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
) : BeforeAllCallback,
    AfterAllCallback {

  private val registryValue = Registry.get(key)
  private val previous = registryValue.asBoolean()

  override fun beforeAll(context: ExtensionContext?) {
    registryValue.setValue(value)
  }

  override fun afterAll(context: ExtensionContext?) {
    registryValue.setValue(previous)
  }
}
