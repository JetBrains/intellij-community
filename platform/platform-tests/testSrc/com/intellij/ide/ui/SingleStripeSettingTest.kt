// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.toolWindow.SingleStripeHelper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** Checks that the readers and the writers of the single stripe use [UISettings.experimentalSingleStripe] and its key. */
@TestApplication
class SingleStripeSettingTest {
  private val settings: UISettings
    get() = UISettings.getInstance()
  private var hideToolStripesBeforeTest = false

  @BeforeEach
  fun dropStoredSettings(): Unit = timeoutRunBlocking(60.seconds) {
    hideToolStripesBeforeTest = settings.state.hideToolStripes
    settings.state.hideToolStripes = false
    dropStoredUISettings()
  }

  @AfterEach
  fun restoreSettings(): Unit = timeoutRunBlocking(60.seconds) {
    settings.state.hideToolStripes = hideToolStripesBeforeTest
    dropStoredUISettings()
  }

  @Test
  fun `the hidden tool stripes follow the single stripe`(): Unit = timeoutRunBlocking(60.seconds) {
    assertFalse(settings.hideToolStripes)

    settings.experimentalSingleStripe = true

    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.SINGLE_STRIPE, true)
    assertTrue(settings.hideToolStripes)
    assertTrue(SingleStripeHelper.isSingleStripeEnabled())
  }

  @Test
  fun `the helper stores the single stripe in the key`(): Unit = timeoutRunBlocking(60.seconds) {
    SingleStripeHelper.setSingleStripeEnabled(true)

    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.SINGLE_STRIPE, true)
  }

  @Test
  fun `the helper stores no value that the key already has`(): Unit = timeoutRunBlocking(60.seconds) {
    SingleStripeHelper.setSingleStripeEnabled(false)

    assertEquals(emptyList<Boolean>(), storedUISettingsValues(UISettingsKeyIdentities.SINGLE_STRIPE))
  }

  @Test
  fun `shown tool stripes turn the single stripe off`(): Unit = timeoutRunBlocking(60.seconds) {
    settings.experimentalSingleStripe = true
    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.SINGLE_STRIPE, true)

    settings.hideToolStripes = false

    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.SINGLE_STRIPE, false)
    assertFalse(settings.hideToolStripes)
  }
}
