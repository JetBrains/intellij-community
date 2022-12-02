package com.intellij.remoteDev.util.tests

import com.intellij.remoteDev.util.tests.modelGenerated.RdAgentId
import org.jetbrains.annotations.ApiStatus

/**
 * Stores data from the original test in agent test instance
 */
@ApiStatus.Internal
data class AgentInfo(val agentId: RdAgentId, val testMethodName: String)