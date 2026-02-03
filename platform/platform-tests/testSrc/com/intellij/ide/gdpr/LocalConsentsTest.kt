// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr

import com.intellij.ide.gdpr.localConsents.LocalConsentOptions
import com.intellij.testFramework.junit5.TestApplication
import kotlin.test.*
import org.junit.jupiter.api.Test

/**
 * Tests for LocalConsent functionality
 */
@TestApplication
class LocalConsentsTest {
  private companion object {
    const val JSON_LOCAL_CONSENTS_DATA = """[{"id":"test.local.consent.1","name":"Test Local Consent 1","text":"This is a test local consent option.","accepted":true},{"id":"test.local.consent.2","name":"Test Local Consent 2","text":"This is another test local consent option.","accepted":false}]"""
    
    const val JSON_TRACE_DATA_COLLECTION_COM = """[{"id":"ai.trace.data.collection.and.use.com.policy","name":"Send detailed code-related data","text":"This includes an expanded range of IDE data with associated code snippets, such as AI feature usage, run configurations, and terminal commands.","accepted":false}]"""
    
    const val JSON_TRACE_DATA_COLLECTION_NON_COM = """[{"id":"ai.trace.data.collection.and.use.noncom.policy","name":"Send detailed code-related data","text":"This includes an expanded range of IDE data with associated code snippets, such as AI feature usage, run configurations, and terminal commands.","accepted":false}]"""
    
    const val LOCAL_CONSENT_ID_1 = "test.local.consent.1"
    const val LOCAL_CONSENT_ID_2 = "test.local.consent.2"
    const val TRACE_DATA_COLLECTION_COM_OPTION_ID = "ai.trace.data.collection.and.use.com.policy"
    const val TRACE_DATA_COLLECTION_NON_COM_OPTION_ID = "ai.trace.data.collection.and.use.noncom.policy"
  }

  @Test
  fun testLoadAndReadLocalConsents() {
    val backend = MemoryIOBackend(JSON_LOCAL_CONSENTS_DATA)
    val consents = loadLocalConsents(backend)

    assertEquals(2, consents.size)
    
    val consent1 = lookupLocalConsent(LOCAL_CONSENT_ID_1, consents)
    assertNotNull(consent1)
    assertEquals("Test Local Consent 1", consent1.name)
    assertEquals("This is a test local consent option.", consent1.text)
    assertTrue(consent1.isAccepted)
    
    val consent2 = lookupLocalConsent(LOCAL_CONSENT_ID_2, consents)
    assertNotNull(consent2)
    assertEquals("Test Local Consent 2", consent2.name)
    assertEquals("This is another test local consent option.", consent2.text)
    assertFalse(consent2.isAccepted)
  }

  @Test
  fun testConfirmLocalConsents() {
    val backend = MemoryIOBackend(JSON_LOCAL_CONSENTS_DATA)
    val consents = loadLocalConsents(backend)

    assertEquals(2, consents.size)
    assertTrue(backend.confirmedData.isEmpty())

    val changedConsents = consents.map { it.derive(!it.isAccepted) }
    setLocalConsents(backend, changedConsents)

    assertFalse(backend.confirmedData.isEmpty())

    val reloadedConsents = loadLocalConsents(backend)
    assertEquals(2, reloadedConsents.size)

    for (changedConsent in changedConsents) {
      val loaded = lookupLocalConsent(changedConsent.id, reloadedConsents)
      assertNotNull(loaded)
      assertEquals(changedConsent.isAccepted, loaded.isAccepted)
    }
  }

  @Test
  fun testPermissionUndefined() {
    val backend = MemoryIOBackend(JSON_LOCAL_CONSENTS_DATA)
    
    val permission = getPermission(backend, LOCAL_CONSENT_ID_1)
    assertEquals(ConsentOptions.Permission.UNDEFINED, permission)
  }

  @Test
  fun testPermissionYes() {
    val backend = MemoryIOBackend(JSON_LOCAL_CONSENTS_DATA)
    val consents = loadLocalConsents(backend)
    
    val consent1 = lookupLocalConsent(LOCAL_CONSENT_ID_1, consents)!!
    setLocalConsents(backend, listOf(consent1.derive(true)))
    
    val permission = getPermission(backend, LOCAL_CONSENT_ID_1)
    assertEquals(ConsentOptions.Permission.YES, permission)
  }

  @Test
  fun testPermissionNo() {
    val backend = MemoryIOBackend(JSON_LOCAL_CONSENTS_DATA)
    val consents = loadLocalConsents(backend)
    
    val consent1 = lookupLocalConsent(LOCAL_CONSENT_ID_1, consents)!!
    setLocalConsents(backend, listOf(consent1.derive(false)))
    
    val permission = getPermission(backend, LOCAL_CONSENT_ID_1)
    assertEquals(ConsentOptions.Permission.NO, permission)
  }

  @Test
  fun testTraceDataCollectionComPermission() {
    val backend = MemoryIOBackend(JSON_TRACE_DATA_COLLECTION_COM)
    val consents = loadLocalConsents(backend)
    
    assertEquals(1, consents.size)
    val traceConsent = lookupLocalConsent(TRACE_DATA_COLLECTION_COM_OPTION_ID, consents)
    assertNotNull(traceConsent)
    assertTrue(LocalConsentOptions.condTraceDataCollectionComLocalConsent().test(traceConsent))
    
    assertEquals(ConsentOptions.Permission.UNDEFINED, getPermission(backend, TRACE_DATA_COLLECTION_COM_OPTION_ID))
    
    setLocalConsents(backend, listOf(traceConsent.derive(true)))
    assertEquals(ConsentOptions.Permission.YES, getPermission(backend, TRACE_DATA_COLLECTION_COM_OPTION_ID))
    
    setLocalConsents(backend, listOf(traceConsent.derive(false)))
    assertEquals(ConsentOptions.Permission.NO, getPermission(backend, TRACE_DATA_COLLECTION_COM_OPTION_ID))
  }

  @Test
  fun testTraceDataCollectionNonComPermission() {
    val backend = MemoryIOBackend(JSON_TRACE_DATA_COLLECTION_NON_COM)
    val consents = loadLocalConsents(backend)
    
    assertEquals(1, consents.size)
    val traceConsent = lookupLocalConsent(TRACE_DATA_COLLECTION_NON_COM_OPTION_ID, consents)
    assertNotNull(traceConsent)
    assertTrue(LocalConsentOptions.condTraceDataCollectionNonComLocalConsent().test(traceConsent))
    
    assertEquals(ConsentOptions.Permission.UNDEFINED, getPermission(backend, TRACE_DATA_COLLECTION_NON_COM_OPTION_ID))
    
    setLocalConsents(backend, listOf(traceConsent.derive(true)))
    assertEquals(ConsentOptions.Permission.YES, getPermission(backend, TRACE_DATA_COLLECTION_NON_COM_OPTION_ID))
    
    setLocalConsents(backend, listOf(traceConsent.derive(false)))
    assertEquals(ConsentOptions.Permission.NO, getPermission(backend, TRACE_DATA_COLLECTION_NON_COM_OPTION_ID))
  }

  @Test
  fun testConfirmedLocalConsentToExternalString() {
    val consent = ConfirmedConsent("test.id", Version.UNKNOWN, true, 123456789L)
    val str = consent.toExternalString()
    assertEquals("test.id:unknown:1:123456789", str)
    
    val consentFalse = ConfirmedConsent("test.id", Version.UNKNOWN, false, 987654321L)
    val strFalse = consentFalse.toExternalString()
    assertEquals("test.id:unknown:0:987654321", strFalse)
  }

  @Test
  fun testConfirmedLocalConsentFromString() {
    val consent = ConfirmedConsent.fromString("test.id:unknown:1:123456789")
    assertNotNull(consent)
    assertEquals("test.id", consent.id)
    assertTrue(consent.isAccepted)
    assertEquals(123456789L, consent.acceptanceTime)
    
    val consentFalse = ConfirmedConsent.fromString("another.id:unknown:0:987654321")
    assertNotNull(consentFalse)
    assertEquals("another.id", consentFalse.id)
    assertFalse(consentFalse.isAccepted)
    assertEquals(987654321L, consentFalse.acceptanceTime)
  }

  @Test
  fun testConfirmedLocalConsentFromStringInvalid() {
    assertNull(ConfirmedConsent.fromString("invalid"))
    assertNull(ConfirmedConsent.fromString("test.id:invalid:123"))
    assertNull(ConfirmedConsent.fromString("test.id:1:invalid"))
  }

  @Test
  fun testMultipleConfirmedLocalConsents() {
    val backend = MemoryIOBackend(JSON_LOCAL_CONSENTS_DATA)
    val consents = loadLocalConsents(backend)

    val consent1 = lookupLocalConsent(LOCAL_CONSENT_ID_1, consents)!!.derive(true)
    val consent2 = lookupLocalConsent(LOCAL_CONSENT_ID_2, consents)!!.derive(false)
    
    setLocalConsents(backend, listOf(consent1, consent2))

    val reloadedConsents = loadLocalConsents(backend)
    assertEquals(2, reloadedConsents.size)
    
    val loaded1 = lookupLocalConsent(LOCAL_CONSENT_ID_1, reloadedConsents)!!
    assertTrue(loaded1.isAccepted)
    
    val loaded2 = lookupLocalConsent(LOCAL_CONSENT_ID_2, reloadedConsents)!!
    assertFalse(loaded2.isAccepted)
  }

  @Test
  fun testLocalConsentDerive() {
    val consent = Consent(
      "test.id",
      Version.UNKNOWN,
      "Test Name",
      "Test Text",
      false,
      false,
      "en"
    )

    val derivedTrue = consent.derive(true)
    assertTrue(derivedTrue.isAccepted)
    assertEquals(consent.id, derivedTrue.id)
    assertEquals(consent.name, derivedTrue.name)
    assertEquals(consent.text, derivedTrue.text)

    val derivedFalse = consent.derive(false)
    assertFalse(derivedFalse.isAccepted)

    val derivedSame = consent.derive(false)
    assertSame(consent, derivedSame)
  }

  private fun lookupLocalConsent(consentId: String, consents: List<Consent>): Consent? {
    return consents.firstOrNull { it.id == consentId }
  }

  private fun loadLocalConsents(backend: MemoryIOBackend): List<Consent> {
    val bundledData = backend.readBundledConsents()
    val confirmedData = backend.readConfirmedConsents()
    
    val confirmed = parseConfirmedLocalConsents(confirmedData)
    val bundled = parseLocalConsentsFromJson(bundledData)
    
    return bundled.map { consent ->
      val confirmedConsent = confirmed[consent.id]
      if (confirmedConsent != null) {
        consent.derive(confirmedConsent.isAccepted)
      } else {
        consent
      }
    }.sortedBy { it.id }
  }

  private fun setLocalConsents(backend: MemoryIOBackend, consents: List<Consent>) {
    val confirmed = parseConfirmedLocalConsents(backend.readConfirmedConsents()).toMutableMap()
    val acceptanceTime = System.currentTimeMillis()
    
    for (consent in consents) {
      confirmed[consent.id] = ConfirmedConsent(consent.id, Version.UNKNOWN, consent.isAccepted, acceptanceTime)
    }
    
    val data = confirmed.values.joinToString(";") { it.toExternalString() }
    backend.writeConfirmedConsents(data)
  }

  private fun getPermission(backend: MemoryIOBackend, consentId: String): ConsentOptions.Permission {
    val confirmed = parseConfirmedLocalConsents(backend.readConfirmedConsents())
    val confirmedConsent = confirmed[consentId]
    
    return if (confirmedConsent == null) {
      ConsentOptions.Permission.UNDEFINED
    } else if (confirmedConsent.isAccepted) {
      ConsentOptions.Permission.YES
    } else {
      ConsentOptions.Permission.NO
    }
  }

  private fun parseLocalConsentsFromJson(json: String): List<Consent> {
    if (json.isEmpty()) return emptyList()
    
    val consents = mutableListOf<Consent>()
    val regex = """"id":"([^"]+)","name":"([^"]+)","text":"([^"]+)","accepted":(true|false)""".toRegex()
    
    regex.findAll(json).forEach { match ->
      val id = match.groupValues[1]
      val name = match.groupValues[2]
      val text = match.groupValues[3]
      val accepted = match.groupValues[4].toBoolean()
      consents.add(Consent(id, Version.UNKNOWN, name, text, accepted, false, "en"))
    }
    
    return consents
  }

  private fun parseConfirmedLocalConsents(data: String): Map<String, ConfirmedConsent> {
    if (data.isEmpty()) return emptyMap()
    
    return data.split(";")
      .mapNotNull { ConfirmedConsent.fromString(it) }
      .associateBy { it.id }
  }

  private class MemoryIOBackend(
    private val bundledData: String,
  ) : ConsentOptions.IOBackend {
    var confirmedData: String = ""
      private set

    override fun writeDefaultConsents(data: String) {
    }

    override fun readDefaultConsents(): String = ""

    override fun readBundledConsents(): String = bundledData

    override fun readLocalizedBundledConsents(): String? = null

    override fun writeConfirmedConsents(data: String) {
      confirmedData = data
    }

    override fun readConfirmedConsents(): String = confirmedData
  }
}
