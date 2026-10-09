// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.UrlFilter
import com.intellij.openapi.project.Project
import com.jediterm.terminal.model.hyperlinks.HyperlinkFilter
import com.jediterm.terminal.model.hyperlinks.LinkResult

internal class Osc8UrlHyperlinkFilter(private val project: Project, private val widget: JBTerminalWidget): HyperlinkFilter {
  private val delegate: Filter = UrlFilter(project)

  override fun apply(line: String): LinkResult? {
    return delegate.applyFilter(line, line.length)?.let {
      convertLinkResult(it, project, widget)
    }
  }
}
