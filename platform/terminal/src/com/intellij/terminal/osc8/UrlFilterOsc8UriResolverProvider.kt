// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.osc8

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.UrlFilter
import com.intellij.openapi.project.Project

/**
 * Recognizes the URLs that the platform [UrlFilter] recognizes.
 */
internal class UrlFilterOsc8UriResolverProvider : Osc8UriResolverProvider {
  override fun getResolvers(project: Project): List<Osc8UriResolver> = listOf(UrlFilterOsc8UriResolver(project))
}

private class UrlFilterOsc8UriResolver(project: Project) : Osc8UriResolver {
  private val urlFilter = UrlFilter(project)

  /**
   * Treats [uri] as a link only if [UrlFilter] matches it over its whole length.
   */
  override fun resolve(uri: String): HyperlinkInfo? {
    val result = urlFilter.applyFilter(uri, uri.length) ?: return null
    return result.resultItems.firstOrNull {
      it.highlightStartOffset == 0 && it.highlightEndOffset == uri.length && it.hyperlinkInfo != null
    }?.hyperlinkInfo
  }
}
