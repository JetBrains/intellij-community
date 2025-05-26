package com.intellij.remoteDev.tests

import com.intellij.remoteDev.tests.modelGenerated.RdAgentInfo
import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.Method
import java.util.*

/**
 * This internal interface should be implemented by distributed tests
 * Agent uses this interface to replay test and perform required test operations
 */
@ApiStatus.Internal
interface DistributedTestPlayer {
  fun initAgent(agent: RdAgentInfo, method: Method): Map<String, Queue<AgentAction>>
}
