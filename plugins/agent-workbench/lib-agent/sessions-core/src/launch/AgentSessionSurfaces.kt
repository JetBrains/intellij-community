// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.launch

import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.util.Locale

private val AGENT_SESSION_SURFACE_ID_REGEX = Regex("[a-z][a-z0-9._-]*")

@ApiStatus.Internal
@JvmInline
value class AgentSessionSurfaceId private constructor(val value: String) {
  companion object {
    fun from(value: String): AgentSessionSurfaceId {
      val normalized = normalizeAgentSessionSurfaceValue(value)
      require(normalized != null && AGENT_SESSION_SURFACE_ID_REGEX.matches(normalized)) {
        "Invalid surface id '$value'. Expected: ${AGENT_SESSION_SURFACE_ID_REGEX.pattern}"
      }
      return AgentSessionSurfaceId(normalized)
    }

    fun fromOrNull(value: String?): AgentSessionSurfaceId? {
      val normalized = normalizeAgentSessionSurfaceValue(value) ?: return null
      return if (AGENT_SESSION_SURFACE_ID_REGEX.matches(normalized)) AgentSessionSurfaceId(normalized) else null
    }
  }

  override fun toString(): String = value
}

@ApiStatus.Internal
const val AGENT_SESSION_SURFACE_TERMINAL: String = "terminal"

@ApiStatus.Internal
const val AGENT_SESSION_SURFACE_ACP: String = "acp"

@ApiStatus.Internal
data class AgentSessionSurfaceDescriptor(
  val id: AgentSessionSurfaceId,
)

@ApiStatus.Internal
interface AgentSessionSurfaceContributor {
  fun getSurfaces(): List<AgentSessionSurfaceDescriptor>
}

@ApiStatus.Internal
object AgentSessionSurfaces {
  val TERMINAL: AgentSessionSurfaceId = AgentSessionSurfaceId.from(AGENT_SESSION_SURFACE_TERMINAL)
  val ACP: AgentSessionSurfaceId = AgentSessionSurfaceId.from(AGENT_SESSION_SURFACE_ACP)

  private val EP = ExtensionPointName<AgentSessionSurfaceContributor>("com.intellij.agent.workbench.sessionSurfaceContributor")
  private val BUILT_IN_DESCRIPTORS = listOf(
    AgentSessionSurfaceDescriptor(TERMINAL),
    AgentSessionSurfaceDescriptor(ACP),
  )

  fun all(): List<AgentSessionSurfaceDescriptor> {
    val contributed = if (EP.hasAnyExtensions()) EP.extensionList.flatMap { contributor -> contributor.getSurfaces() } else emptyList()
    return (BUILT_IN_DESCRIPTORS + contributed).distinctBy { descriptor -> descriptor.id }
  }

  fun find(id: AgentSessionSurfaceId): AgentSessionSurfaceDescriptor? {
    return all().firstOrNull { descriptor -> descriptor.id == id }
  }
}

@ApiStatus.Internal
fun normalizeAgentSessionSurfaceId(surfaceId: String?): String? {
  return AgentSessionSurfaceId.fromOrNull(surfaceId)?.value
}

@ApiStatus.Internal
fun effectiveAgentSessionSurfaceId(
  descriptor: AgentSessionProviderDescriptor,
  surfaceId: String?,
): AgentSessionSurfaceId {
  return effectiveAgentSessionSurfaceId(descriptor, AgentSessionSurfaceId.fromOrNull(surfaceId))
}

@ApiStatus.Internal
fun effectiveAgentSessionSurfaceId(
  descriptor: AgentSessionProviderDescriptor,
  surfaceId: AgentSessionSurfaceId?,
): AgentSessionSurfaceId {
  val supportedSurfaces = descriptor.supportedLaunchSurfaces
  if (surfaceId != null && surfaceId in supportedSurfaces) {
    return surfaceId
  }
  return descriptor.defaultLaunchSurface.takeIf { defaultSurface -> defaultSurface in supportedSurfaces }
         ?: AgentSessionSurfaces.TERMINAL
}

private fun normalizeAgentSessionSurfaceValue(surfaceId: String?): String? {
  return surfaceId?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
}
