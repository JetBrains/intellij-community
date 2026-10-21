// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.osc8

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.ide.CommandLineProcessor
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.project.Project
import com.intellij.terminal.platformTerminalProjectScope
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.launch

/**
 * Recognizes the `jetbrains://` URIs, which the [JBProtocolCommand]s handle.
 */
internal class JbProtocolOsc8UriResolverProvider : Osc8UriResolverProvider {
  override fun getResolvers(project: Project): List<Osc8UriResolver> = listOf(JbProtocolOsc8UriResolver)
}

private object JbProtocolOsc8UriResolver : Osc8UriResolver {
  private const val PREFIX = JBProtocolCommand.SCHEME + URLUtil.SCHEME_SEPARATOR

  override fun resolve(uri: String): HyperlinkInfo? {
    if (!uri.startsWith(PREFIX) || uri.length == PREFIX.length) return null
    return JbProtocolHyperlinkInfo(uri)
  }
}

/**
 * Runs [uri] in this IDE, as an external `jetbrains://` request runs.
 * [CommandLineProcessor.processProtocolCommand] shows a notification if the command fails.
 */
private class JbProtocolHyperlinkInfo(private val uri: String) : HyperlinkInfo {
  override fun navigate(project: Project) {
    platformTerminalProjectScope(project).launch {
      CommandLineProcessor.processProtocolCommand(uri)
    }
  }
}
