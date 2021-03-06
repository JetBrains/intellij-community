// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import org.junit.Assert.*
import org.junit.Test

class TrustedProjectsTest {

  @Test
  fun `origin from url`() {
    val tests = listOf(
      "https://github.com/JetBrains/intellij.git" to "github.com/JetBrains",
      "ssh://git@github.com:JetBrains/intellij.git" to "github.com/JetBrains",
      "git@github.com:JetBrains/intellij.git" to "github.com/JetBrains",
      "ssh://git@git.example.com/intellij.git" to "git.example.com",
      "https://192.168.1.1/project.git" to "192.168.1.1"
    )
    for ((url, expected) in tests) {
      assertEquals("Incorrectly parsed $url", expected, getOriginFromUrl(url)?.host)
    }
  }

  @Test
  fun `trusted host with protocol`() {
    val tests = listOf(
      HostTest("https://github.com/JetBrains", "https://github.com/JetBrains/intellij.git", true),
      HostTest("https://github.com/JetBrains/", "https://github.com/JetBrains/intellij.git", true),
      HostTest("https://github.com/JetBrains/", "ssh://git@github.com:JetBrains/intellij.git", false),
      HostTest("ssh://git@github.com:JetBrains", "https://github.com/JetBrains/intellij.git", false),
      HostTest("ssh://git@github.com:JetBrains/", "ssh://git@github.com:JetBrains/intellij.git", true),
      HostTest("github.com/JetBrains", "https://github.com/JetBrains/intellij.git", true),
      HostTest("github.com/JetBrains", "ssh://git@github.com:JetBrains/intellij.git", true),
      HostTest("https://domain/my", "https://domain/my-dir.git", false),
      HostTest("192.168.1.1", "https://192.168.1.1/project.git", true)
    )

    val settings = TrustedHostsSettings()
    for ((trustedHost, url, isTrusted) in tests) {
      settings.setHostTrusted(trustedHost, true)
      if (isTrusted) {
        assertTrue("$url should be trusted comparing to host $trustedHost", settings.isUrlTrusted(url))
      }
      else {
        assertFalse("$url should be untrusted comparing to host $trustedHost", settings.isUrlTrusted(url))
      }
      settings.setHostTrusted(trustedHost, false)
    }
  }

  @Test
  fun `trusted host as explicit url`() {
    val settings = TrustedHostsSettings()
    settings.setHostTrusted("https://github.com/JetBrains/intellij.git", true)
    assertTrue(settings.isUrlTrusted("https://github.com/JetBrains/intellij.git"))
  }

  private data class HostTest(val trustedHost: String, val urlToTest: String, val isTrusted: Boolean)
}