// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SeOpenAiKeyLeaseTest {
  private val store = SeOpenAiKeyStore { error("The lease reads no stored key") }

  @Test
  fun `a personal key goes as a bearer token to the OpenAI API`() {
    val lease = openAiKeyLease("sk-test", store)

    assertEquals("https://api.openai.com/v1", lease.url)
    assertEquals(mapOf("Authorization" to "Bearer sk-test"), lease.headers)
  }

  @Test
  fun `the endpoint prints no key`() {
    assertFalse(openAiKeyLease("sk-test", store).toString().contains("sk-test"))
  }
}
