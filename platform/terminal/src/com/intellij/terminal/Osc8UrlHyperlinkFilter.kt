// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.openapi.project.Project
import com.intellij.terminal.osc8.CompositeOsc8UriResolverWrapper
import com.jediterm.terminal.model.hyperlinks.HyperlinkFilter
import com.jediterm.terminal.model.hyperlinks.LinkResult
import com.jediterm.terminal.model.hyperlinks.LinkResultItem

/**
 * Decides whether the target of an OSC 8 hyperlink is a link in the Classic terminal.
 */
internal class Osc8UrlHyperlinkFilter(private val project: Project, private val widget: JBTerminalWidget) : HyperlinkFilter {
  private val uriResolverWrapper = CompositeOsc8UriResolverWrapper(project, createScope(widget))

  /**
   * Called possibly under the terminal text buffer lock, see [com.jediterm.terminal.model.JediTerminal.setUrlHyperlinkFilter].
   * The URI resolvers accept that: see [com.intellij.terminal.osc8.Osc8UriResolver].
   */
  override fun apply(line: String): LinkResult? {
    val hyperlinkInfo = uriResolverWrapper.getCompositeResolver().resolve(line) ?: return null
    return LinkResult(LinkResultItem(0, line.length, convertInfo(hyperlinkInfo, project, widget)))
  }
}
