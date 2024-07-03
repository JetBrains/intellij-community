package com.intellij.remoteDev.tests

import com.intellij.remoteDev.tests.modelGenerated.RdTestComponentData
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * Represents a single test action in a distributed test
 */
@ApiStatus.Internal
class AgentAction(val timeout: Duration,
                  val coroutineContextGetter: () -> CoroutineContext,
                  val requestFocusBeforeStart: Boolean? = null,
                  val action: suspend (AgentContext).(List<String>?) -> String?)

@ApiStatus.Internal
class AgentActionGetComponentData(val timeout: Duration,
                                  val coroutineContextGetter: () -> CoroutineContext,
                                  val requestFocusBeforeStart: Boolean? = null,
                                  val action: suspend (AgentContext).(List<String>?) -> RdTestComponentData)