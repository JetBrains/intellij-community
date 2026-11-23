// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.platform.whatsNew.WhatsNewInVisionContentProvider.TabGroup
import com.intellij.platform.whatsNew.WhatsNewUrlChange.AnchorNavigated
import com.intellij.platform.whatsNew.WhatsNewUrlChange.PageChanged
import com.intellij.platform.whatsNew.WhatsNewUrlChange.TabSelected
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhatsNewVisionUrlTest {
  private val tabGroups = listOf(TabGroup("generaltabs", listOf("key-highlights", "dotnet")), TabGroup("gamedevtabs", listOf("unity")))
  private val anchorIds = setOf("net-11-support", "c-15-support")

  private fun changes(oldUrl: String?, newUrl: String, fallbackPageId: String = "Key-updates") =
    findUrlChanges(oldUrl, newUrl, fallbackPageId, tabGroups, anchorIds)

  @Test
  fun `multipage URL gives the page, the tabs and the anchor`() {
    assertEquals(
      WhatsNewVisionUrl("Key-updates", mapOf("generaltabs" to "dotnet", "gamedevtabs" to "unity"), "net-11-support"),
      WhatsNewVisionUrl.parse("file:///jbcefbrowser?generaltabs=dotnet&gamedevtabs=unity#/Key-updates#net-11-support"),
    )
    assertEquals(WhatsNewVisionUrl("Key-updates", emptyMap(), null), WhatsNewVisionUrl.parse("file:///jbcefbrowser#/Key-updates"))
    assertEquals(WhatsNewVisionUrl(null, emptyMap(), "dotnet"), WhatsNewVisionUrl.parse("file:///jbcefbrowser#/#dotnet"))
  }

  @Test
  fun `single page URL gives the tabs and the anchor`() {
    assertEquals(
      WhatsNewVisionUrl(null, mapOf("generaltabs" to "dotnet"), "net-11-support"),
      WhatsNewVisionUrl.parse("file:///jbcefbrowser/123?generaltabs=dotnet#net-11-support"),
    )
    assertEquals(WhatsNewVisionUrl(null, emptyMap(), null), WhatsNewVisionUrl.parse("file:///jbcefbrowser/123#url=about:blank"))
    assertEquals(WhatsNewVisionUrl(null, emptyMap(), null), WhatsNewVisionUrl.parse(null))
  }

  @Test
  fun `page change`() {
    assertEquals(
      listOf(PageChanged("Key-updates", "Game-development")),
      changes("file:///jbcefbrowser#/Key-updates", "file:///jbcefbrowser#/Game-development"),
    )
  }

  @Test
  fun `tab change keeps the page`() {
    assertEquals(
      listOf(TabSelected("Key-updates", "generaltabs", "dotnet")),
      changes("file:///jbcefbrowser?generaltabs=key-highlights#/Key-updates", "file:///jbcefbrowser?generaltabs=dotnet#/Key-updates"),
    )
    assertEquals(
      listOf(TabSelected("Key-updates", "generaltabs", "dotnet")),
      changes("file:///jbcefbrowser#/Key-updates", "file:///jbcefbrowser?generaltabs=dotnet#/Key-updates"),
    )
  }

  @Test
  fun `anchor click keeps the page`() {
    assertEquals(
      listOf(AnchorNavigated("Key-updates", "net-11-support")),
      changes("file:///jbcefbrowser#/Key-updates", "file:///jbcefbrowser#/Key-updates#net-11-support"),
    )
    assertEquals(
      listOf(AnchorNavigated("Key-updates", "c-15-support")),
      changes("file:///jbcefbrowser#/Key-updates#net-11-support", "file:///jbcefbrowser#/Key-updates#c-15-support"),
    )
  }

  @Test
  fun `tab change after an anchor click gives no anchor change`() {
    assertEquals(
      listOf(TabSelected("Key-updates", "generaltabs", "dotnet")),
      changes("file:///jbcefbrowser#/Key-updates#net-11-support", "file:///jbcefbrowser?generaltabs=dotnet#/Key-updates#net-11-support"),
    )
  }

  @Test
  fun `single page uses the fallback page id`() {
    assertEquals(
      listOf(AnchorNavigated("default_id", "net-11-support")),
      changes(null, "file:///jbcefbrowser/123#net-11-support", fallbackPageId = "default_id"),
    )
    assertEquals(
      listOf(TabSelected("default_id", "generaltabs", "dotnet")),
      changes("file:///jbcefbrowser/123#url=about:blank", "file:///jbcefbrowser/123?generaltabs=dotnet#url=about:blank", fallbackPageId = "default_id"),
    )
  }

  @Test
  fun `unknown tab and anchor ids are left out`() {
    assertEquals(emptyList<WhatsNewUrlChange>(), changes("file:///jbcefbrowser#/Key-updates", "file:///jbcefbrowser?generaltabs=other#/Key-updates"))
    assertEquals(emptyList<WhatsNewUrlChange>(), changes("file:///jbcefbrowser#/Key-updates", "file:///jbcefbrowser?othertabs=dotnet#/Key-updates"))
    assertEquals(emptyList<WhatsNewUrlChange>(), changes("file:///jbcefbrowser#/Key-updates", "file:///jbcefbrowser#/Key-updates#other"))
    assertEquals(emptyList<WhatsNewUrlChange>(), changes(null, "file:///jbcefbrowser/123#url=about:blank", fallbackPageId = "default_id"))
  }
}
