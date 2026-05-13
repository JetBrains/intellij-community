// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import org.jetbrains.annotations.ApiStatus

/**
 * Marker exception raised by the session-load pipeline when a provider's CLI is not available
 * (per [AgentSessionProviderDescriptor.isCliAvailable]). The refresh coordinator translates this
 * into the provider's [AgentSessionProviderDescriptor.cliMissingMessageKey] warning that shows up
 * as a `SessionTreeId.Warning` row in the sessions tree — so providers whose own session sources
 * do not surface a "binary not found" error (Claude/Junie filesystem-based discovery) still
 * produce a CLI-not-found warning instead of silently appearing empty.
 */
@ApiStatus.Internal
class AgentProviderCliMissingException(val provider: AgentSessionProvider) :
  IllegalStateException("CLI for provider ${provider.value} is not available")
