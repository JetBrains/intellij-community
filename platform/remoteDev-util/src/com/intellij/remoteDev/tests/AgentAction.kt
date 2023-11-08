package com.intellij.remoteDev.tests

import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * Represents a single test action in a distributed test
 */
@ApiStatus.Internal
class AgentAction(val title: String,
                  val timeout: Duration,
                  val coroutineContext: CoroutineContext,
                  val action: suspend (AgentContext) -> String?)