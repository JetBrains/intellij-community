// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew
import org.junit.Assert
import org.junit.Test

class WhatsNewContentTest {

  @Test
  fun `Content version comparison`() {
    val nonEap = WhatsNewContent.ContentVersion("2024", "1", eap = null, hash = null)
    val eap1 = WhatsNewContent.ContentVersion("2024", "1", eap = 1, hash = null)
    val eap2 = WhatsNewContent.ContentVersion("2024", "1", eap = 2, hash = null)
    val build242 = WhatsNewContent.ContentVersion("2024", "2", eap = null, hash = null)
    Assert.assertTrue(nonEap > eap1)
    Assert.assertTrue(eap1 < nonEap)
    Assert.assertTrue(eap1 < eap2)
    Assert.assertTrue(eap2 > eap1)
    Assert.assertEquals(eap1, eap1)
    Assert.assertEquals(nonEap, nonEap)
    Assert.assertTrue(build242 > nonEap)
    Assert.assertTrue(build242 > eap1)

    val release = WhatsNewContent.ContentVersion("2024", "1", eap = null, hash = null)
    val bugfix = WhatsNewContent.ContentVersion("2024", "1.1", eap = null, hash = null)
    val bigBugfix = WhatsNewContent.ContentVersion("2024", "10.1", eap = null, hash = null)
    val smallBugfix = WhatsNewContent.ContentVersion("2024", "9.1", eap = null, hash = null)
    Assert.assertTrue(bugfix > release)
    Assert.assertTrue(bigBugfix > bugfix)
    Assert.assertTrue(smallBugfix < bigBugfix)
  }
}
