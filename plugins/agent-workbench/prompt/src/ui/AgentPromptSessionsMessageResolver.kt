// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.AbstractBundle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.Locale
import java.util.ResourceBundle

internal class AgentPromptSessionsMessageResolver(
  private val fallbackClassLoader: ClassLoader,
) {
  fun resolve(@NonNls key: String, bridge: AgentSessionProviderBridge? = null, vararg params: Any): @Nls String? {
    val classLoaders = linkedSetOf<ClassLoader>()
    bridge?.javaClass?.classLoader?.let(classLoaders::add)
    classLoaders.add(fallbackClassLoader)

    classLoaders.forEach { classLoader ->
      val bundle = runCatching {
        ResourceBundle.getBundle("messages.AgentSessionsBundle", Locale.getDefault(), classLoader)
      }.getOrNull() ?: return@forEach

      val resolved = AbstractBundle.messageOrNull(bundle, key, *params) ?: return@forEach
      return resolved
    }

    return null
  }
}
