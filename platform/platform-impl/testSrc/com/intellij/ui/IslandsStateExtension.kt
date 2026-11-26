// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Enables the Islands theme state of [IslandsState] for each test, and restores the previous state after it.
 *
 * A test can call [IslandsState.setEnabled] to check another theme.
 */
internal class IslandsStateExtension : BeforeEachCallback, AfterEachCallback {
  private var enabled = false
  private var custom = false

  override fun beforeEach(context: ExtensionContext) {
    enabled = IslandsState.isEnabled()
    custom = IslandsState.isCustomEnabled()
    IslandsState.setEnabled(true, false)
  }

  override fun afterEach(context: ExtensionContext) {
    IslandsState.setEnabled(enabled, custom)
  }
}
