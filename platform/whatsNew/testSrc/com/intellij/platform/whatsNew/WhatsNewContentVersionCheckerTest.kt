// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewContentVersionCheckerTest {
  @Test
  fun `Comparison by version should take the highest version`() {
    val version1 = WhatsNewContent.ContentVersion("2020", "9.1", null, null)
    val version2 = WhatsNewContent.ContentVersion("2020", "10.1", null, null)
    assertTrue(WhatsNewContentVersionChecker.shouldShowWhatsNew(version1, version2))
  }

  @Test
  fun `Comparison by version and hash should ignore the hashes`() {
    var version1 = WhatsNewContent.ContentVersion("2020", "9.1", null, null)
    var version2 = WhatsNewContent.ContentVersion("2020", "10.1", null, "123123")
    assertTrue(WhatsNewContentVersionChecker.shouldShowWhatsNew(version1, version2))

    version1 = WhatsNewContent.ContentVersion("2020", "9.1", null, "123123")
    version2 = WhatsNewContent.ContentVersion("2020", "10.1", null, null)
    assertTrue(WhatsNewContentVersionChecker.shouldShowWhatsNew(version1, version2))

    version1 = WhatsNewContent.ContentVersion("2020", "10.1", null, "123123")
    version2 = WhatsNewContent.ContentVersion("2020", "10.1", null, null)
    assertFalse(WhatsNewContentVersionChecker.shouldShowWhatsNew(version1, version2))

    version1 = WhatsNewContent.ContentVersion("2020", "10.1", null, null)
    version2 = WhatsNewContent.ContentVersion("2020", "10.1", null, "123123")
    assertFalse(WhatsNewContentVersionChecker.shouldShowWhatsNew(version1, version2))
  }

  @Test
  fun `Comparison by two hashes just compares hashes and ignores versions if the stored is older`() {
    var version1 = WhatsNewContent.ContentVersion("2020", "9.1", null, "123123")
    var version2 = WhatsNewContent.ContentVersion("2020", "10.1", null, "123123")
    assertFalse(WhatsNewContentVersionChecker.shouldShowWhatsNew(version1, version2))

    version1 = WhatsNewContent.ContentVersion("2020", "9.1", null, "1231234")
    version2 = WhatsNewContent.ContentVersion("2020", "10.1", null, "123123")
    assertTrue(WhatsNewContentVersionChecker.shouldShowWhatsNew(version1, version2))

    version1 = WhatsNewContent.ContentVersion("2020", "10.1", null, "1231234")
    version2 = WhatsNewContent.ContentVersion("2020", "9.1", null, "123123")
    assertFalse(WhatsNewContentVersionChecker.shouldShowWhatsNew(version1, version2))
  }

  @Test
  fun `Comparison with future version`() {
    var storedVersion = WhatsNewContent.ContentVersion("2020", "9.1", null, "123123")
    var newVersion = WhatsNewContent.ContentVersion("2020", "8.1", null, "321321")
    // On the one hand, the current content differs from the stored. On the other hand, the one stored is *newer*.
    assertFalse(WhatsNewContentVersionChecker.shouldShowWhatsNew(storedVersion, newVersion))

    storedVersion = WhatsNewContent.ContentVersion("2020", "9.1", null, "123123")
    newVersion = WhatsNewContent.ContentVersion("2020", "9.1", null, "321321")
    // Hash-only change ⇒ show the page.
    assertTrue(WhatsNewContentVersionChecker.shouldShowWhatsNew(storedVersion, newVersion))
  }
}