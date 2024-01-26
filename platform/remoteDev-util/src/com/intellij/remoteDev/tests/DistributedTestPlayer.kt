package com.intellij.remoteDev.tests

import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.Method
import java.util.Queue

/**
 * This internal interface should be implemented by distributed tests
 * Agent uses this interface to replay test and perform required test operations
 */
@ApiStatus.Internal
interface DistributedTestPlayer {
  fun initAgent(agent: AgentInfo): Map<String, Queue<AgentAction>>

  fun performInit(method: Method)
}
