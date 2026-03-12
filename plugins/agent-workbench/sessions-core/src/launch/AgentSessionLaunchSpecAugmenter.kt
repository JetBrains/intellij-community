// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.launch

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface AgentSessionLaunchSpecAugmenter {
  suspend fun augment(
    projectPath: String,
    provider: AgentSessionProvider,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec
}

private class AgentSessionLaunchSpecAugmenterRegistry

private val LOG = logger<AgentSessionLaunchSpecAugmenterRegistry>()
private val AGENT_SESSION_LAUNCH_SPEC_AUGMENTER_EP: ExtensionPointName<AgentSessionLaunchSpecAugmenter> =
  ExtensionPointName("com.intellij.agent.workbench.sessionLaunchSpecAugmenter")

object AgentSessionLaunchSpecAugmenters {
  private val testOverrideMutex = Mutex()

  @Volatile
  private var testAugmenterOverride: AgentSessionLaunchSpecAugmenter? = null

  suspend fun augment(
    projectPath: String,
    provider: AgentSessionProvider,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    val augmenter = testAugmenterOverride ?: findRegisteredAugmenter() ?: return launchSpec
    return augmenter.augment(projectPath = projectPath, provider = provider, launchSpec = launchSpec)
  }

  suspend fun <T> withAugmenterForTest(augmenter: AgentSessionLaunchSpecAugmenter?, action: suspend () -> T): T {
    return testOverrideMutex.withLock {
      val previous = testAugmenterOverride
      testAugmenterOverride = augmenter
      try {
        action()
      }
      finally {
        testAugmenterOverride = previous
      }
    }
  }

  private fun findRegisteredAugmenter(): AgentSessionLaunchSpecAugmenter? {
    val augmenters = try {
      AGENT_SESSION_LAUNCH_SPEC_AUGMENTER_EP.extensionList
    }
    catch (t: IllegalStateException) {
      LOG.debug("Session launch spec augmenter EP is unavailable in this context", t)
      return null
    }
    catch (t: IllegalArgumentException) {
      LOG.debug("Session launch spec augmenter EP is unavailable in this context", t)
      return null
    }

    if (augmenters.size > 1) {
      LOG.warn("Multiple session launch spec augmenters registered; using first: ${augmenters.map { it::class.java.name }}")
    }
    return augmenters.firstOrNull()
  }
}
