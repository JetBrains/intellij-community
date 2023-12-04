package com.intellij.remoteDev.tests

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.remoteDev.tests.modelGenerated.RdAgentInfo
import com.jetbrains.rd.framework.IProtocol
import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to all essential entities on this agent required to perform test operations
 */
interface AgentContext {
  val agentId: RdAgentInfo
  val protocol: IProtocol

  val application: Application
    get() = ApplicationManagerEx.getApplication()
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
internal class HostAgentContextImpl(
  override val agentId: RdAgentInfo,
  override val protocol: IProtocol,
) : HostContext

@ApiStatus.Internal
internal class ClientAgentContextImpl(
  override val agentId: RdAgentInfo,
  override val protocol: IProtocol,
) : ClientContext

@ApiStatus.Internal
internal class GatewayAgentContextImpl(
  override val agentId: RdAgentInfo,
  override val protocol: IProtocol,
) : GatewayContext
