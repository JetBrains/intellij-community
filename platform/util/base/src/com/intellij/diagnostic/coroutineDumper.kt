// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * @param deduplicateTrees deduplicate identical coroutine job trees in the dump. If there are multiple identical repetitions of a job tree,
 * such tree will have `-[x<number> of]` prefix in the dump.
 */
@JvmOverloads
fun dumpCoroutines(scope: CoroutineScope? = null, stripDump: Boolean = true, deduplicateTrees: Boolean = true): String? {
  if (!DebugProbes.isInstalled) {
    return null
  }
  val charset = StandardCharsets.UTF_8.name()
  val outputStream = ByteArrayOutputStream()
  PrintStream(BufferedOutputStream(outputStream), true, charset).use { out ->
    val jobTree = jobTrees(scope).toList()
    dumpCoroutines(jobTree, out, stripDump, deduplicateTrees)
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
 * @param stripDump if set to true, will omit stackframes that do not provide useful debug information, but come up frequently in traces.
 *                  Examples of such stackframes:
 * ```
 * at kotlinx.coroutines.DelayKt.awaitCancellation(Delay.kt:148)
 * at kotlinx.coroutines.channels.AbstractChannel.receiveCatching-JP2dKIU(AbstractChannel.kt:633)
 * at kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllImpl$FlowKt__ChannelsKt(Channels.kt:51)
 * at kotlinx.coroutines.flow.internal.ChannelFlow$collect$2.invokeSuspend(ChannelFlow.kt:123)
 * ```
 *
 * @param deduplicateTrees if set to true, will deduplicate identical coroutine job trees. Two job trees are considered equal, if their
 *  string representations, after job address removal, are equal.
 *  Example of a deduplicated coroutine tree:
 *  ```
 *  - "run activity":ProducerCoroutine{Completing} [run activity, Dispatchers.Default]
 *  	-[x2 of] "run activity":StandaloneCoroutine{Active}, state: SUSPENDED [run activity, Dispatchers.Default]
 *  		at kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllImpl$FlowKt__ChannelsKt(Channels.kt:36)
 *  		at kotlinx.coroutines.flow.internal.ChannelFlow$collect$2.invokeSuspend(ChannelFlow.kt:123)
 *  		at kotlinx.coroutines.flow.internal.ChannelLimitedFlowMerge$collectTo$2$1.invokeSuspend(Merge.kt:96)
 *  		- "run activity":ProducerCoroutine{Active}, state: SUSPENDED [run activity, Dispatchers.Default]
 *  			at kotlinx.coroutines.channels.ProduceKt.awaitClose(Produce.kt:153)
 *  			at com.intellij.dependencytoolwindow.CoroutineUtilsKt$isAvailableFlow$1.invokeSuspend(CoroutineUtils.kt:61)
 *  			at kotlinx.coroutines.flow.CallbackFlowBuilder.collectTo(Builders.kt:334)
 *  			at kotlinx.coroutines.flow.internal.ChannelFlow$collectToFun$1.invokeSuspend(ChannelFlow.kt:60)
 *  ```
 */
private fun dumpCoroutines(jobTrees: List<JobTree>, out: PrintStream, stripDump: Boolean, deduplicateTrees: Boolean) {
  val representationTrees: List<JobRepresentationTree>
  if (!deduplicateTrees) {
    representationTrees = jobTrees.map { it.toRepresentation(stripDump) }
  }
  else {
    representationTrees =
      JobTree(Job(), null, jobTrees) // virtual common super parent so that deduplication works on roots too
        .toRepresentation(stripDump)
        .deduplicate()
        .children.toList()
  }

  for (representation in representationTrees) {
    out.println()
    representation.write(out)
  }
}

private tailrec fun Job.rootJob(): Job {
  @Suppress("DEPRECATION_ERROR")
  val parentJob = (this as JobSupport).parentHandle?.parent
                  ?: return this
  return parentJob.rootJob()
}

private data class JobTree(val job: Job, val debugInfo: DebugCoroutineInfo?, val children: List<JobTree>)

private fun jobTrees(scope: CoroutineScope? = null): Sequence<JobTree> {
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
      yieldAll(buildJobTrees(job, jobToStack))
    }
  }
}

private fun buildJobTrees(
  job: Job,
  jobToStack: Map<Job, DebugCoroutineInfo>
): List<JobTree> {
  val info = jobToStack[job]
  if (info === null && job is ScopeCoroutine<*>) {
    // don't yield ScopeCoroutine without info, such as `coroutineScope` or `withContext`
    return job.children.flatMap { buildJobTrees(it, jobToStack) }.toList()
  }
  else {
    return listOf(JobTree(job, info, job.children.flatMap { buildJobTrees(it, jobToStack) }.toList()))
  }
}

private data class JobRepresentation(
  val coroutineName: String?,
  val job: String,
  val state: String?,
  val context: String?,
  val trace: List<StackTraceElement>
) {
  private fun indent(out: PrintStream, level: Int) {
    out.print("\t".repeat(level))
  }

  private fun writeHeader(out: PrintStream, level: Int, additionalInfo: String) {
    indent(out, level)
    out.print("-$additionalInfo ")
    coroutineName?.let {
      // repeat format from kotlinx.coroutines.AbstractCoroutine.nameString
      out.print("\"${it}\":")
    }
    out.print(job)
    state?.let {
      out.print(", state: ${it}")
    }
    context?.let {
      out.print(" [$it]")
    }
    out.println()
  }

  private fun writeTrace(out: PrintStream, level: Int) {
    for (stackFrame in trace) {
      indent(out, level)
      out.println("\tat $stackFrame")
    }
  }

  fun write(out: PrintStream, level: Int, additionalInfo: String) {
    writeHeader(out, level, additionalInfo)
    writeTrace(out, level)
  }
}

private open class JobRepresentationTree(val job: JobRepresentation, open val children: Collection<JobRepresentationTree>) {
  open fun write(out: PrintStream, indentLevel: Int = 0) {
    job.write(out, indentLevel, "")
    children.forEach { it.write(out, indentLevel + 1) }
  }
}

private class DeduplicatedJobRepresentationTree(
  val count: Int, job: JobRepresentation, override val children: Set<DeduplicatedJobRepresentationTree>
) : JobRepresentationTree(job, children) {
  override fun write(out: PrintStream, indentLevel: Int) {
    val countInfo = if (count == 1) "" else "[x$count of]"
    job.write(out, indentLevel, countInfo)
    children.forEach { it.write(out, indentLevel + 1) }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DeduplicatedJobRepresentationTree

    return count == other.count && job == other.job && children == other.children
  }

  // hash can be accessed multiple times during deduplication
  private val treeHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
    var hash = count
    hash = 31 * hash + job.hashCode()
    hash = 31 * hash + children.hashCode()
    hash
  }

  override fun hashCode(): Int = treeHash
}

private fun JobTree.toRepresentation(stripTrace: Boolean): JobRepresentationTree {
  val context: CoroutineContext = when {
    job is CoroutineScope -> job.coroutineContext
    debugInfo !== null -> debugInfo.context
    else -> EmptyCoroutineContext
  }
  val name = if (job is AbstractCoroutine<*> && DEBUG) {
    // in DEBUG the name is displayed as part of `job.toString()` for AbstractCoroutine
    // see kotlinx.coroutines.AbstractCoroutine.nameString
    null
  }
  else if (job.javaClass.name == "com.intellij.platform.util.coroutines.ChildScope") {
    // ChildScope already renders the name in its `toString()`
    null
  }
  else {
    context[CoroutineName]?.name
  }
  val interestingContext = context.minusKey(Job).minusKey(CoroutineName)
  val contextString = if (interestingContext != EmptyCoroutineContext) {
    interestingContext.fold("") { acc, element ->
      if (acc.isEmpty()) element.toString() else "$acc, $element"
    }
  }
  else null
  val trace = debugInfo?.let { traceToDump(it, stripTrace) } ?: emptyList()
  val representation = JobRepresentation(name, job.toString(), debugInfo?.state, contextString, trace)
  return JobRepresentationTree(representation, children.map { it.toRepresentation(stripTrace) })
}

private fun JobRepresentationTree.deduplicate(): DeduplicatedJobRepresentationTree =
  DeduplicatedJobRepresentationTree(
    1,
    job.withoutJobAddress(),
    children
      .map(JobRepresentationTree::deduplicate) // all counts = 1
      .groupBy { it }
      .map { DeduplicatedJobRepresentationTree(it.value.size, it.key.job, it.key.children) }.toSet()
  )

private fun JobRepresentation.withoutJobAddress(): JobRepresentation {
  val ind = job.lastIndexOf("}@")
  if (ind == -1) return this
  assert(job.substring(ind + 2, job.length).all { it.isLetterOrDigit() })
  return JobRepresentation(coroutineName, job.substring(0, ind + 1), state, context, trace)
}

private fun traceToDump(info: DebugCoroutineInfo, stripTrace: Boolean): List<StackTraceElement> {
  val trace = info.lastObservedStackTrace
  if (stripTrace && info.state == SUSPENDED) {
    return stripCoroutineTrace(trace)
  }
  return DebugProbesImpl.enhanceStackTraceWithThreadDump(info, trace)
}