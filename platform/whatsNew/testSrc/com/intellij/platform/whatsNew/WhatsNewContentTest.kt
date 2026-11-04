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

  @Test
  fun `Air button alt text variable follows the welcome screen project`() {
    val key = $$"$__VISION_PAGE_SETTINGS_IS_AIR_BUTTON_ALT_TEXT_ENABLED__$"
    val page = WhatsNewInVisionContentProvider.Page(
      id = 1,
      actions = emptyList(),
      html = "isAirButtonAltTextEnabled: '$key'",
      languages = emptyList(),
      publicVars = listOf(WhatsNewInVisionContentProvider.PublicVar(key, "")),
    )
    val content = WhatsNewVisionContent(WhatsNewInVisionContentProvider(), page)
    Assert.assertEquals("isAirButtonAltTextEnabled: 'true'", content.getContent(isWelcomeScreenProject = true, isChinaRegion = false, isAiNotAllowed = false))
    Assert.assertEquals("isAirButtonAltTextEnabled: 'false'", content.getContent(isWelcomeScreenProject = false, isChinaRegion = false, isAiNotAllowed = false))
  }

  @Test
  fun `Chinese variable follows the IDE region`() {
    val key = $$"$__VISION_PAGE_SETTINGS_IS_CHINESE__$"
    val page = WhatsNewInVisionContentProvider.Page(
      id = 1,
      actions = emptyList(),
      html = "isChinese: '$key'",
      languages = emptyList(),
      publicVars = listOf(WhatsNewInVisionContentProvider.PublicVar(key, "")),
    )
    val content = WhatsNewVisionContent(WhatsNewInVisionContentProvider(), page)
    Assert.assertEquals("isChinese: 'true'", content.getContent(isWelcomeScreenProject = false, isChinaRegion = true, isAiNotAllowed = false))
    Assert.assertEquals("isChinese: 'false'", content.getContent(isWelcomeScreenProject = false, isChinaRegion = false, isAiNotAllowed = false))
  }

  @Test
  fun `AI not allowed variable follows the Air plugin state`() {
    val key = $$"$__VISION_PAGE_SETTINGS_IS_AI_NOT_ALLOWED__$"
    val page = WhatsNewInVisionContentProvider.Page(
      id = 1,
      actions = emptyList(),
      html = "isAiNotAllowed: '$key'",
      languages = emptyList(),
      publicVars = listOf(WhatsNewInVisionContentProvider.PublicVar(key, "")),
    )
    val content = WhatsNewVisionContent(WhatsNewInVisionContentProvider(), page)
    Assert.assertEquals("isAiNotAllowed: 'true'", content.getContent(isWelcomeScreenProject = false, isChinaRegion = false, isAiNotAllowed = true))
    Assert.assertEquals("isAiNotAllowed: 'false'", content.getContent(isWelcomeScreenProject = false, isChinaRegion = false, isAiNotAllowed = false))
  }
}
