// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.launch

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Returns the **local** IDE's streamable-HTTP MCP URL — i.e. the one that the agent CLI
 * AWB is about to spawn should connect to. The implementation lives in a module that
 * depends on the MCP Server plugin (`container-mcp`) and reads the URL straight from the
 * in-process `McpServerConnectionAddressProvider`.
 *
 * "Local" is intentional: AWB always launches from inside the IDE that owns the project,
 * so the in-process URL is by definition the correct one. There is no fallback to
 * cross-process IDE discovery — picking a different IDE's URL would route the agent at
 * an instance that doesn't hold the AWB session state for this launch.
 */
fun interface McpStreamUrlProvider {
  fun getStreamUrl(): String?

  companion object {
    private val EP: ExtensionPointName<McpStreamUrlProvider> =
      ExtensionPointName("com.intellij.agent.workbench.mcpStreamUrlProvider")

    fun resolve(): String? {
      for (provider in EP.extensionList) {
        val url = provider.getStreamUrl()
        if (url != null) return url
      }
      return null
    }
  }
}
