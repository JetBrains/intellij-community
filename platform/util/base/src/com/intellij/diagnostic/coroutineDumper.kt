// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@file:OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)

package com.intellij.diagnostic

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.CoroutineInfo
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.debug.internal.DebugCoroutineInfo
import kotlinx.coroutines.debug.internal.DebugProbesImpl
import kotlinx.coroutines.internal.ScopeCoroutine
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun enableCoroutineDump() {
  DebugProbes.enableCreationStackTraces = false
  DebugProbes.install()
}

@JvmOverloads
fun dumpCoroutines(scope: CoroutineScope? = null): String? {
  if (!DebugProbes.isInstalled) {
    return null
  }
  val charset = StandardCharsets.UTF_8.name()
  val outputStream = ByteArrayOutputStream()
  PrintStream(BufferedOutputStream(outputStream), true, charset).use { out ->
    val jobTree = jobTree(scope).toList()
    dumpCoroutines(jobTree, out)
  }
  return outputStream.toString(charset)
}

/**
 * Example output:
 * ```
 * - BlockingCoroutine{Completing}@2a5c959b [BlockingEventLoop@1d80b9da]
 * 	- StandaloneCoroutine{Active}@154d1536, state: SUSPENDED, name: 'root rb 0' [BlockingEventLoop@1d80b9da]
 * 		at kotlinx.coroutines.DelayKt.awaitCancellation(Delay.kt:148)
 * 		at com.intellij.internal.TestCoroutineProgressAction$cancellableBGProgress$1$1$2$2.invokeSuspend(TestCoroutineProgressAction.kt:91)
 * 	- StandaloneCoroutine{Completing}@1b7e4b1f, name: 'root rb 1' [BlockingEventLoop@1d80b9da]
 * 		- StandaloneCoroutine{Active}@56b4c42, state: SUSPENDED, name: 'root rb 1:0' [BlockingEventLoop@1d80b9da]
 * 			at kotlinx.coroutines.DelayKt.awaitCancellation(Delay.kt:148)
 * 			at com.intellij.internal.TestCoroutineProgressAction$cancellableBGProgress$1$1$2$3$1.invokeSuspend(TestCoroutineProgressAction.kt:95)
 * 	- StandaloneCoroutine{Active}@491608fb, state: SUSPENDED, name: 'root rb 2' [BlockingEventLoop@1d80b9da]
 * 		at kotlinx.coroutines.DelayKt.awaitCancellation(Delay.kt:148)
 * 		at com.intellij.internal.TestCoroutineProgressAction$cancellableBGProgress$1$1$2$4.invokeSuspend(TestCoroutineProgressAction.kt:102)
 * 		- StandaloneCoroutine{Active}@2d37afba, state: SUSPENDED, name: 'root rb 2:0' [BlockingEventLoop@1d80b9da]
 * 			at kotlinx.coroutines.DelayKt.awaitCancellation(Delay.kt:148)
 * 			at com.intellij.internal.TestCoroutineProgressAction$cancellableBGProgress$1$1$2$4$1.invokeSuspend(TestCoroutineProgressAction.kt:100)
 * ```
 *
 * This implementation uses internal coroutine APIs, because public ones are not enough:
 * - [DebugProbes.dumpCoroutines] prints available stacks for all coroutines, but in a flat list.
 * - [DebugProbes.printJob]/[DebugProbes.jobToString] dumps job hierarchy, but with a last stack frame only.
 * - [DebugProbes.dumpCoroutinesInfo] returns flat list of [CoroutineInfo].
 * It's possible to compute the required mapping from [Job] to [CoroutineInfo],
 * but [CoroutineInfo.lastObservedStackTrace] doesn't enhance the trace with dump of last thread,
 * which is crucial for detecting stuck [runBlocking] coroutines.
 */
private fun dumpCoroutines(jobTree: List<JobTreeNode>, out: PrintStream) {
  for ((job: Job, info: DebugCoroutineInfo?, level: Int) in jobTree) {
    if (level == 0) {
      out.println()
    }

    val indent = "\t".repeat(level)

    out.print(indent) // -- header line start
    out.print("- ")
    out.print(job)
    if (info !== null) {
      out.print(", state: ${info.state}")
    }
    val context: CoroutineContext = when {
      job is AbstractCoroutine<*> -> job.context
      info !== null -> info.context
      else -> EmptyCoroutineContext
    }
    context[CoroutineName]?.name?.let {
      out.print(", name: '$it'")
    }
    val interestingContext = context.minusKey(Job).minusKey(CoroutineName)
    if (interestingContext != EmptyCoroutineContext) {
      val contextString = interestingContext.fold("") { acc, element ->
        if (acc.isEmpty()) element.toString() else "$acc, $element"
      }
      out.print(" [$contextString]")
    }
    out.println() // -- header line end

    if (info !== null) {
      val trace = DebugProbesImpl.enhanceStackTraceWithThreadDump(info, info.lastObservedStackTrace)
      for (stackFrame in trace) {
        out.println("$indent\tat $stackFrame")
      }
    }
  }
}

private data class JobTreeNode(
  val job: Job,
  val debugInfo: DebugCoroutineInfo?,
  val level: Int,
)

private fun jobTree(scope: CoroutineScope? = null): Sequence<JobTreeNode> {
  val coroutineInfos = DebugProbesImpl.dumpCoroutinesInfo()

  // adapted from kotlinx.coroutines.debug.internal.DebugProbesImpl.hierarchyToString
  val jobToStack: Map<Job, DebugCoroutineInfo> = coroutineInfos
    .filter { it.context[Job] != null }
    .associateBy { it.context.job }

  val rootJobs = if (scope != null) {
    setOf(scope.coroutineContext.job)
  }
  else {
    jobToStack.keys.mapTo(LinkedHashSet()) {
      it.rootJob()
    }
  }

  return sequence {
    for (job in rootJobs) {
      jobTree(job, jobToStack, 0)
    }
  }
}

private fun Job.rootJob(): Job {
  var result = this
  while (true) {
    val parent = (result as @Suppress("DEPRECATION_ERROR") JobSupport).parentHandle?.parent
    result = parent
             ?: return result
  }
}

private suspend fun SequenceScope<JobTreeNode>.jobTree(
  job: Job,
  jobToStack: Map<Job, DebugCoroutineInfo>,
  level: Int
) {
  val info = jobToStack[job]
  val nextLevel = if (info === null && job is ScopeCoroutine<*>) {
    // don't yield ScopeCoroutine without info, such as `coroutineScope` or `withContext`
    level
  }
  else {
    yield(JobTreeNode(job, info, level))
    level + 1
  }
  for (child in job.children) {
    jobTree(child, jobToStack, nextLevel)
  }
}
