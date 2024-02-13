package com.intellij.remoteDev.tests

import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * Represents a single test action in a distributed test
 */
@ApiStatus.Internal
class AgentAction(val timeout: Duration,
                  val coroutineContext: CoroutineContext,
                  val syncBeforeStart: Boolean,
                  val requestFocusBeforeStart: Boolean? = null,
                  val action: suspend (AgentContext) -> String?)