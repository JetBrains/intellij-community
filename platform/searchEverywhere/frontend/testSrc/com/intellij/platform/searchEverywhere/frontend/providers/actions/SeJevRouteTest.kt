// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SeJevRouteTest {
  private val store = SeJevKeyStore { error("The lease reads no stored key") }

  @Test
  fun `an OpenRouter key goes to the Decision API of OpenRouter`() {
    val route = SeJevRoute.forKey("sk-or-v1-test")

    assertEquals(SeJevRoute.OPENROUTER, route)
    assertEquals("https://openrouter.ai/api/alpha/decisions", route.api.operationUrl(route.baseUrl))
    assertEquals("~typesafe/jev-latest", route.defaultModel)
  }

  @Test
  fun `another key goes to the System One API of TypeSafe`() {
    val route = SeJevRoute.forKey("apikey_test")

    assertEquals(SeJevRoute.TYPESAFE, route)
    assertEquals("https://api.typesafe.ai/v1/systemone", route.api.operationUrl(route.baseUrl))
    assertEquals("jev-latest", route.defaultModel)
  }

  @Test
  fun `a personal key goes as a bearer token, and the endpoint prints no key`() {
    val lease = apiKeyLease(SeJevRoute.TYPESAFE.baseUrl, "apikey_test", store)

    assertEquals(mapOf("Authorization" to "Bearer apikey_test"), lease.headers)
    assertFalse(lease.toString().contains("apikey_test"))
  }
}
