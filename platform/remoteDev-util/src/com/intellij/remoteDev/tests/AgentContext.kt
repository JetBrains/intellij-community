package com.intellij.remoteDev.tests

import com.intellij.openapi.application.Application
import com.intellij.openapi.project.Project
import com.intellij.remoteDev.tests.modelGenerated.RdAgentId
import com.jetbrains.rd.framework.IProtocol
import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to all essential entities on this agent required to perform test operations
 */
interface AgentContext {
  val agentId: RdAgentId
  val application: Application
  val projectOrNull: Project?
  val protocol: IProtocol
  val project: Project
    get() = projectOrNull ?: error("Project shouldn't be requested for the projectless application")
  companion object {
    fun create(agentId: RdAgentId,
               application: Application,
               projectOrNull: Project?,
               protocol: IProtocol) : AgentContext {
      return AgentContextImpl(agentId, application, projectOrNull, protocol)
    }
  }
}

@ApiStatus.Internal
interface HostContext : AgentContext
@ApiStatus.Internal
interface GatewayContext : AgentContext
@ApiStatus.Internal
interface ClientContext : AgentContext

// If you need some extra data or behavior for a particular class, e.g. HostContext should have some host specific data, just derive from
// the AgentContextImpl and derive it also from HostContext
// then create proper inheritor in AgentContext.create() depending on agentId
@ApiStatus.Internal
internal class AgentContextImpl(
  override val agentId: RdAgentId,
  override val application: Application,
  override val projectOrNull: Project?,
  override val protocol: IProtocol
) : HostContext, ClientContext, GatewayContext