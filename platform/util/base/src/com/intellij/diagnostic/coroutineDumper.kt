// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@file:OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)

package com.intellij.diagnostic

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.CoroutineInfo
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.debug.internal.DebugCoroutineInfo
import kotlinx.coroutines.debug.internal.DebugProbesImpl
import kotlinx.coroutines.debug.internal.SUSPENDED
import kotlinx.coroutines.internal.ScopeCoroutine
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Internal
const val COROUTINE_DUMP_HEADER: @NonNls String = "---------- Coroutine dump ----------"
internal const val COROUTINE_DUMP_HEADER_STRIPPED: @NonNls String = "---------- Coroutine dump (stripped) ----------"

@Internal
fun isCoroutineDumpHeader(line: String): Boolean {
  return line == COROUTINE_DUMP_HEADER || line == COROUTINE_DUMP_HEADER_STRIPPED
}

fun enableCoroutineDump() {
  runCatching {
    DebugProbes.enableCreationStackTraces = false
    DebugProbes.install()
  }
}

/**
 * @param stripDump whether to remove stackframes from coroutine dump that have no useful debug information.
 */
@JvmOverloads
fun dumpCoroutines(scope: CoroutineScope? = null, stripDump: Boolean = true): String? {
  if (!DebugProbes.isInstalled) {
    return null
  }
  val charset = StandardCharsets.UTF_8.name()
  val outputStream = ByteArrayOutputStream()
  PrintStream(BufferedOutputStream(outputStream), true, charset).use { out ->
    val jobTree = jobTree(scope).toList()
    dumpCoroutines(jobTree, out, stripDump)
  }
  return outputStream.toString(charset)
}

/**
 * Example output:
 * ```
 * - BlockingCoroutine{Completing}@2a5c959b [BlockingEventLoop@1d80b9da]
 * 	- "root rb 0":StandaloneCoroutine{Active}@154d1536, state: SUSPENDED [BlockingEventLoop@1d80b9da]
 * 		at kotlinx.coroutines.DelayKt.awaitCancellation(Delay.kt:148)
 * 		at com.intellij.internal.TestCoroutineProgressAction$cancellableBGProgress$1$1$2$2.invokeSuspend(TestCoroutineProgressAction.kt:91)
 * 	- "root rb 1":StandaloneCoroutine{Completing}@1b7e4b1f [BlockingEventLoop@1d80b9da]
 * 		- "root rb 1:0":StandaloneCoroutine{Active}@56b4c42, state: SUSPENDED [BlockingEventLoop@1d80b9da]
 * 			at kotlinx.coroutines.DelayKt.awaitCancellation(Delay.kt:148)
 * 			at com.intellij.internal.TestCoroutineProgressAction$cancellableBGProgress$1$1$2$3$1.invokeSuspend(TestCoroutineProgressAction.kt:95)
 * 	- "root rb 2":StandaloneCoroutine{Active}@491608fb, state: SUSPENDED [BlockingEventLoop@1d80b9da]
 * 		at kotlinx.coroutines.DelayKt.awaitCancellation(Delay.kt:148)
 * 		at com.intellij.internal.TestCoroutineProgressAction$cancellableBGProgress$1$1$2$4.invokeSuspend(TestCoroutineProgressAction.kt:102)
 * 		- "root rb 2:0":StandaloneCoroutine{Active}@2d37afba, state: SUSPENDED [BlockingEventLoop@1d80b9da]
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
 *
 * @param stripDump if set to true, will omit stackframes that does not provide useful debug information, but come up frequently in traces.
 *                  Examples of such stackframes:
 * ```
 * at kotlinx.coroutines.DelayKt.awaitCancellation(Delay.kt:148)
 * at kotlinx.coroutines.channels.AbstractChannel.receiveCatching-JP2dKIU(AbstractChannel.kt:633)
 * at kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllImpl$FlowKt__ChannelsKt(Channels.kt:51)
 * at kotlinx.coroutines.flow.internal.ChannelFlow$collect$2.invokeSuspend(ChannelFlow.kt:123)
 * ```
 */
@OptIn(InternalCoroutinesApi::class)
private fun dumpCoroutines(jobTree: List<JobTreeNode>, out: PrintStream, stripDump: Boolean) {
  for ((job: Job, info: DebugCoroutineInfo?, level: Int) in jobTree) {
    if (level == 0) {
      out.println()
    }

    val indent = "\t".repeat(level)

    out.print(indent) // -- header line start
    out.print("- ")
    val context: CoroutineContext = when {
      job is AbstractCoroutine<*> -> job.context
      info !== null -> info.context
      else -> EmptyCoroutineContext
    }
    if (job is AbstractCoroutine<*> && DEBUG) {
      // in DEBUG the name is displayed as part of `job.toString()` for AbstractCoroutine
      // see kotlinx.coroutines.AbstractCoroutine.nameString
    }
    else {
      context[CoroutineName]?.let {
        out.print("\"${it.name}\":") // repeat format from kotlinx.coroutines.AbstractCoroutine.nameString
      }
    }
    out.print(job)
    if (info !== null) {
      out.print(", state: ${info.state}")
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
      for (stackFrame in traceToDump(info, stripDump)) {
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

@OptIn(InternalCoroutinesApi::class)
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

private fun traceToDump(info: DebugCoroutineInfo, stripTrace: Boolean): List<StackTraceElement> {
  val trace = info.lastObservedStackTrace
  if (stripTrace && info.state == SUSPENDED) {
    return stripCoroutineTrace(trace)
  }
  return DebugProbesImpl.enhanceStackTraceWithThreadDump(info, trace)
}