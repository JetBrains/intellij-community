// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import java.net.URLDecoder

/**
 * The state of a Vision page that its URL holds.
 *
 * A multipage artifact uses a hash router: `file:///jbcefbrowser?generaltabs=dotnet#/Key-updates#net-11-support`.
 * A single page artifact has no route: `file:///jbcefbrowser/123?generaltabs=dotnet#net-11-support`.
 * Before the first anchor, the fragment of a single page is `url=about:blank`, which `loadHTML` adds.
 *
 * @property pageId the multipage id of the route, or `null` without a route
 * @property tabs the selected tab of each tab group, from the URL query
 * @property anchor the anchor after the route, or the whole fragment without a route
 */
internal data class WhatsNewVisionUrl(val pageId: String?, val tabs: Map<String, String>, val anchor: String?) {
  companion object {
    private val EMPTY = WhatsNewVisionUrl(pageId = null, tabs = emptyMap(), anchor = null)
    private const val LOAD_HTML_FRAGMENT_PREFIX = "url="

    fun parse(url: String?): WhatsNewVisionUrl {
      if (url == null) return EMPTY
      val fragment = url.substringAfter('#', missingDelimiterValue = "")
      val query = url.substringBefore('#').substringAfter('?', missingDelimiterValue = "")
      val tabs = query.split('&').mapNotNull { parameter ->
        val name = parameter.substringBefore('=')
        val value = parameter.substringAfter('=', missingDelimiterValue = "")
        if (name.isEmpty() || value.isEmpty()) null else decode(name) to decode(value)
      }.toMap()

      return when {
        fragment.startsWith('/') -> {
          val route = fragment.substring(1)
          WhatsNewVisionUrl(route.substringBefore('#').ifEmpty { null }, tabs, decodeAnchor(route.substringAfter('#', "")))
        }
        fragment.startsWith(LOAD_HTML_FRAGMENT_PREFIX) -> WhatsNewVisionUrl(pageId = null, tabs, anchor = null)
        else -> WhatsNewVisionUrl(pageId = null, tabs, decodeAnchor(fragment))
      }
    }

    private fun decodeAnchor(text: String): String? = if (text.isEmpty()) null else decode(text)

    private fun decode(text: String): String =
      try {
        URLDecoder.decode(text, Charsets.UTF_8)
      }
      catch (_: IllegalArgumentException) {
        text
      }
  }
}

internal sealed interface WhatsNewUrlChange {
  data class PageChanged(val oldPageId: String, val newPageId: String) : WhatsNewUrlChange
  data class TabSelected(val pageId: String, val tabsGroupId: String, val tabId: String) : WhatsNewUrlChange
  data class AnchorNavigated(val pageId: String, val anchorId: String) : WhatsNewUrlChange
}

/**
 * Returns the user actions between two URLs of a Vision page.
 * A tab or an anchor that the artifact does not list is left out, so that no unknown text goes to FUS.
 *
 * @param fallbackPageId the page id of a URL without a route
 */
internal fun findUrlChanges(
  oldUrl: String?,
  newUrl: String,
  fallbackPageId: String,
  tabGroups: List<WhatsNewInVisionContentProvider.TabGroup>,
  anchorIds: Collection<String>,
): List<WhatsNewUrlChange> {
  val old = WhatsNewVisionUrl.parse(oldUrl)
  val new = WhatsNewVisionUrl.parse(newUrl)
  val oldPageId = old.pageId ?: fallbackPageId
  val newPageId = new.pageId ?: fallbackPageId
  val changes = ArrayList<WhatsNewUrlChange>()

  if (new.pageId != null && newPageId != oldPageId) {
    changes.add(WhatsNewUrlChange.PageChanged(oldPageId, newPageId))
  }
  for ((groupId, tabId) in new.tabs) {
    if (old.tabs[groupId] != tabId && tabGroups.any { it.id == groupId && tabId in it.values }) {
      changes.add(WhatsNewUrlChange.TabSelected(newPageId, groupId, tabId))
    }
  }
  val anchor = new.anchor
  if (anchor != null && anchor != old.anchor && anchor in anchorIds) {
    changes.add(WhatsNewUrlChange.AnchorNavigated(newPageId, anchor))
  }
  return changes
}
