package com.intellij.remoteDev.tests

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.remoteDev.tests.modelGenerated.RdAgentInfo
import com.jetbrains.rd.framework.IProtocol
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

/**
 * Provides access to all essential entities on this agent required to perform test operations
 */
interface AgentContext: CoroutineScope {
  val agentInfo : RdAgentInfo
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
  override val agentInfo: RdAgentInfo,
  override val protocol: IProtocol,
  override val coroutineContext: CoroutineContext,
) : HostContext

@ApiStatus.Internal
internal class ClientAgentContextImpl(
  override val agentInfo: RdAgentInfo,
  override val protocol: IProtocol,
  override val coroutineContext: CoroutineContext,
) : ClientContext

@ApiStatus.Internal
internal class GatewayAgentContextImpl(
  override val agentInfo: RdAgentInfo,
  override val protocol: IProtocol,
  override val coroutineContext: CoroutineContext,
) : GatewayContext
