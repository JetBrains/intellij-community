// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyConfigurationTest {
  @Test
  fun testExceptionsMatcher() {
    val matcher = ProxyConfiguration.buildProxyExceptionsMatcher("192.168.*, domain.com")
    assertTrue(matcher.test("192.168.0.1"))
    assertTrue(matcher.test("192.168.1"))
    assertTrue(matcher.test("domain.com"))
    assertTrue(matcher.test("192.168.0.5.domain.com"))

    assertFalse(matcher.test("192.168"))
    assertFalse(matcher.test("192.180.0.1"))
    assertFalse(matcher.test("sub.domain.com"))
    assertFalse(matcher.test("domain.com.not.really"))
  }
}