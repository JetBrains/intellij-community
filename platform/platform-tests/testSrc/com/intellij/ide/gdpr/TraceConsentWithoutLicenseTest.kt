// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr

import com.intellij.ide.ConsentOptionsProvider
import com.intellij.ide.gdpr.localConsents.LocalConsentOptions
import com.intellij.ide.gdpr.trace.TraceConsentManager
import com.intellij.ide.gdpr.ui.consents.AiDataCollectionExternalSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.AppUIUtil
import com.intellij.ui.LicensingFacade
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Files

@TestApplication
@Timeout(30)
class TraceConsentWithoutLicenseTest {
  private val settings = TestExternalSettings()
  private val manager = TestTraceConsentManager()
  private val provider: ConsentOptionsProvider get() = service()
  private val consentFile = LocalConsentOptions.getConfirmedLocalConsentsFileForTests()
  private var savedConsents: ByteArray? = null
  private var savedLicense: LicensingFacade? = null

  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable) {
    savedLicense = LicensingFacade.getInstance()
    savedConsents = if (Files.exists(consentFile)) Files.readAllBytes(consentFile) else null
    Files.deleteIfExists(consentFile)
    LicensingFacade.setInstance(LicensingFacade())
    AiDataCollectionExternalSettings.overrideForTest(settings, disposable)
    ExtensionTestUtil.maskExtensions(TraceConsentManager.EP_NAME, listOf(manager), disposable)
  }

  @AfterEach
  fun tearDown() {
    LicensingFacade.setInstance(savedLicense)
    val saved = savedConsents
    if (saved == null) Files.deleteIfExists(consentFile)
    else Files.write(consentFile, saved)
  }

  @Test
  fun `missing metadata requires an explicit commercial consent`() {
    assertFalse(provider.isTraceDataCollectionAllowed)
    writeConsent(commercial = false, accepted = true)
    assertFalse(provider.isTraceDataCollectionAllowed)
    writeConsent(commercial = true, accepted = false)
    assertFalse(provider.isTraceDataCollectionAllowed)
    writeConsent(commercial = true, accepted = true)
    assertTrue(provider.isTraceDataCollectionAllowed)
    LicensingFacade.setInstance(null)
    assertTrue(provider.isTraceDataCollectionAllowed)
  }

  @Test
  fun `missing metadata remains blocked without provider support`() {
    writeConsent(commercial = true, accepted = true)
    settings.supported = false
    assertFalse(provider.isTraceDataCollectionAllowed)
  }

  @Test
  fun `external restrictions and display eligibility remain required`() {
    writeConsent(commercial = true, accepted = true)
    settings.disabled = true
    assertFalse(provider.isTraceDataCollectionAllowed)
    settings.disabled = false
    manager.displayAllowed = false
    assertFalse(provider.isTraceDataCollectionAllowed)
    manager.displayAllowed = true
    assertTrue(provider.isTraceDataCollectionAllowed)
  }

  @Test
  fun `existing license restrictions and consent type remain effective`() {
    writeConsent(commercial = true, accepted = true)
    setMetadata(type = 'C', agreement = 'N')
    assertFalse(provider.isTraceDataCollectionAllowed)
    setMetadata(type = 'C', agreement = 'X')
    assertTrue(provider.isTraceDataCollectionAllowed)
    setMetadata(type = 'F', agreement = 'X')
    assertFalse(provider.isTraceDataCollectionAllowed)
    writeConsent(commercial = false, accepted = true)
    assertTrue(provider.isTraceDataCollectionAllowed)
  }

  @Test
  fun `malformed metadata does not use the missing metadata exception`() {
    writeConsent(commercial = true, accepted = true)
    LicensingFacade.setInstance(LicensingFacade().apply { metadata = "short" })
    assertFalse(provider.isTraceDataCollectionAllowed)
  }

  @Test
  fun `settings show the commercial consent without metadata when the manager allows it`() {
    val commercial = LocalConsentOptions.condTraceDataCollectionComLocalConsent()
    val nonCommercial = LocalConsentOptions.condTraceDataCollectionNonComLocalConsent()
    val shown = AppUIUtil.loadLocalConsentsAsConsentsForEditing()
    assertTrue(shown.any { commercial.test(it) })
    assertFalse(shown.any { nonCommercial.test(it) })
    manager.displayAllowed = false
    assertFalse(AppUIUtil.loadLocalConsentsAsConsentsForEditing().any { commercial.test(it) || nonCommercial.test(it) })
  }

  private fun writeConsent(commercial: Boolean, accepted: Boolean) {
    val id = if (commercial) "ai.trace.data.collection.and.use.com.policy" else "ai.trace.data.collection.and.use.noncom.policy"
    Files.createDirectories(consentFile.parent)
    Files.writeString(consentFile, ConfirmedConsent(id, Version.UNKNOWN, accepted, System.currentTimeMillis()).toExternalString())
  }

  private fun setMetadata(type: Char, agreement: Char) {
    LicensingFacade.setInstance(LicensingFacade().apply { metadata = "A".repeat(10) + type + "A".repeat(16) + agreement + 'X' })
  }

  private class TestExternalSettings : AiDataCollectionExternalSettings {
    var supported = true
    var disabled = false
    override fun supportsTraceConsentWithoutLicense(): Boolean = supported
    override fun isForciblyDisabled(): Boolean = disabled
    override fun getForciblyDisabledDescription(): String? = null
  }

  private class TestTraceConsentManager : TraceConsentManager {
    var displayAllowed = true
    override fun canDisplayTraceConsent(): Boolean = displayAllowed
    override fun canCollectTraceConsent(): Boolean = displayAllowed
    override fun isTraceConsentEnabled(): Boolean = displayAllowed
  }
}
