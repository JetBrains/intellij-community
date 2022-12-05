package com.intellij.remoteDev.tests

import com.jetbrains.rd.framework.impl.RdTask
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a single test action in a distributed test
 */
@ApiStatus.Internal
class AgentAction(val action: (AgentContext) -> RdTask<Boolean>, val title: String)