package com.intellij.remoteDev.tests

import com.intellij.remoteDev.tests.modelGenerated.RdAgentInfo
import org.jetbrains.annotations.ApiStatus

/**
 * Stores data from the original test in agent test instance
 */
@ApiStatus.Internal
data class AgentInfo(val rdAgentInfo: RdAgentInfo, val testClassName: String, val testMethodName: String)