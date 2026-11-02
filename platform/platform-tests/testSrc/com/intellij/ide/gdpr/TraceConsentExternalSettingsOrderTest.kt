// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr

import com.intellij.ide.ConsentOptionsProvider
import com.intellij.ide.gdpr.trace.TraceConsentManager
import com.intellij.ide.gdpr.ui.consents.AiDataCollectionExternalSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.LicensingFacade
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** The TRACE check must not ask the AI plugin for its restriction when the license already denies TRACE. */
@TestApplication
@Timeout(30)
class TraceConsentExternalSettingsOrderTest {
  private val settings = CountingExternalSettings()
  private val manager = TestTraceConsentManager()
  private val provider: ConsentOptionsProvider get() = service()
  private var savedLicense: LicensingFacade? = null

  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable) {
    savedLicense = LicensingFacade.getInstance()
    AiDataCollectionExternalSettings.overrideForTest(settings, disposable)
    ExtensionTestUtil.maskExtensions(TraceConsentManager.EP_NAME, listOf(manager), disposable)
  }

  @AfterEach
  fun tearDown() {
    LicensingFacade.setInstance(savedLicense)
  }

  @Test
  fun `malformed metadata skips the external settings`() {
    LicensingFacade.setInstance(LicensingFacade().apply { metadata = "short" })
    assertFalse(provider.isTraceDataCollectionAllowed)
    assertEquals(0, settings.calls)
  }

  @Test
  fun `a license that denies data collection skips the external settings`() {
    setMetadata(agreement = 'N')
    assertFalse(provider.isTraceDataCollectionAllowed)
    assertEquals(0, settings.calls)
  }

  @Test
  fun `a hidden TRACE consent skips the external settings`() {
    setMetadata(agreement = 'Y')
    manager.displayAllowed = false
    assertFalse(provider.isTraceDataCollectionAllowed)
    assertEquals(0, settings.calls)
  }

  @Test
  fun `an allowed license applies the external restriction`() {
    setMetadata(agreement = 'Y')
    assertTrue(provider.isTraceDataCollectionAllowed)
    settings.disabled = true
    assertFalse(provider.isTraceDataCollectionAllowed)
    assertEquals(2, settings.calls)
  }

  private fun setMetadata(agreement: Char) {
    LicensingFacade.setInstance(LicensingFacade().apply { metadata = "A".repeat(10) + 'C' + "A".repeat(16) + agreement + 'X' })
  }

  private class CountingExternalSettings : AiDataCollectionExternalSettings {
    var disabled = false
    var calls = 0

    override fun isForciblyDisabled(): Boolean {
      calls++
      return disabled
    }

    override fun getForciblyDisabledDescription(): String? = null
  }

  private class TestTraceConsentManager : TraceConsentManager {
    var displayAllowed = true
    override fun canDisplayTraceConsent(): Boolean = displayAllowed
    override fun canCollectTraceConsent(): Boolean = displayAllowed
    override fun isTraceConsentEnabled(): Boolean = displayAllowed
  }
}