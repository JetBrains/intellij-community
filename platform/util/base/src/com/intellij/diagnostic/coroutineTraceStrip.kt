// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.StackframeShrinkVerdict.*
import org.jetbrains.annotations.VisibleForTesting

// Here "library" stands for kotlinx.coroutines library

@VisibleForTesting
fun stripCoroutineTrace(trace: List<StackTraceElement>): List<StackTraceElement> {
  if (trace.isEmpty()) {
    return emptyList()
  }

  return if (trace.all { it.className.startsWith("kotlinx.coroutines.") }) {
    stripLibraryOnlyTrace(trace)
  }
  else {
    stripTraceWithNonLibraryFrames(trace)
  }
}

private fun stripTraceWithNonLibraryFrames(trace: List<StackTraceElement>): List<StackTraceElement> {
  val lastNonLibFrameIndex = trace.indexOfLast { !it.className.startsWith("kotlinx.coroutines.") }
  check(lastNonLibFrameIndex != -1)
  return trace
    // drop every library frame that comes after last non-library frame
    .subList(0, lastNonLibFrameIndex + 1)
    .stripStart(nonLibraryCaseStackframeJudge)
}

private fun stripLibraryOnlyTrace(trace: List<StackTraceElement>): List<StackTraceElement> {
  return trace
    .stripStart(libraryStackframeJudge)
    .stripTail(libraryStackframeJudge)
}

private enum class StackframeShrinkVerdict {
  // stackframe must not be omitted
  KEEP,

  // leave at least one stackframe from a continuous group of SHRINK stackframes
  SHRINK,

  // stackframe can be omitted
  OMIT
}

private fun List<StackTraceElement>.stripStart(judge: StackframeJudge): List<StackTraceElement> {
  var startIndex = 0
  while (startIndex < size && judge(this[startIndex]) == OMIT) {
    startIndex++
  }
  while (startIndex + 1 < size &&
         judge(this[startIndex]) == SHRINK &&
         judge(this[startIndex + 1]) == SHRINK) {
    startIndex++
  }
  return subList(startIndex, size)
}

private fun List<StackTraceElement>.stripTail(judge: StackframeJudge): List<StackTraceElement> {
  var afterLastIndex = size
  while (0 <= afterLastIndex - 1 &&
         judge(this[afterLastIndex - 1]) == OMIT) {
    afterLastIndex--
  }
  // TODO: strip SHRINK?
  return subList(0, afterLastIndex)
}

private val StackTraceElement.fullName: String get() = "$className.$methodName"

private typealias StackframeJudge = (StackTraceElement) -> StackframeShrinkVerdict

// override rules in case there are non-library stackframes
private val nonLibraryCaseStackframeJudge: StackframeJudge = { stackframe: StackTraceElement ->
  when (stackframe.fullName) {
    // com.intellij
    "com.intellij.util.CoroutineScopeKt\$namedChildScope\$2\$1.invokeSuspend" -> OMIT
    "com.intellij.util.CoroutineScopeKt\$namedChildScope\$2.invokeSuspend" -> KEEP

    // kotlinx.coroutines.flow override
    "kotlinx.coroutines.flow.StateFlowImpl.collect" -> OMIT
    "kotlinx.coroutines.flow.FlowKt__MergeKt\$flattenConcat$1$1.emit" -> OMIT
    "kotlinx.coroutines.flow.FlowKt__MergeKt\$flatMapConcat\$\$inlined\$map$1$2.emit" -> OMIT
    "kotlinx.coroutines.flow.FlowKt__LimitKt.emitAbort\$FlowKt__LimitKt" -> OMIT
    "kotlinx.coroutines.flow.FlowKt__LimitKt\$take$2$1.emit" -> OMIT

    else -> libraryStackframeJudge(stackframe)
  }
}

private val libraryStackframeJudge: StackframeJudge = { stackframe: StackTraceElement ->
  when (stackframe.fullName) {
    // kotlinx.coroutines.channels
    "kotlinx.coroutines.channels.AbstractChannel.receiveCatching-JP2dKIU" -> OMIT
    "kotlinx.coroutines.channels.ProduceKt.awaitClose" -> OMIT

    // kotlinx.coroutines.flow
    "kotlinx.coroutines.flow.SharedFlowImpl.collect\$suspendImpl" -> OMIT
    "kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllImpl\$FlowKt__ChannelsKt" -> OMIT
    "kotlinx.coroutines.flow.FlowKt__DelayKt\$debounceInternal\$1.invokeSuspend" -> OMIT
    "kotlinx.coroutines.flow.FlowKt__DelayKt\$debounceInternal\$1\$values\$1.invokeSuspend" -> OMIT
    "kotlinx.coroutines.flow.FlowKt__ReduceKt.first" -> OMIT
    "kotlinx.coroutines.flow.CallbackFlowBuilder.collectTo" -> OMIT
    "kotlinx.coroutines.flow.StateFlowImpl.collect" -> SHRINK
    "kotlinx.coroutines.flow.FlowKt__ShareKt\$launchSharing\$1.invokeSuspend" -> SHRINK
    "kotlinx.coroutines.flow.FlowKt__ErrorsKt.catchImpl" -> SHRINK
    "kotlinx.coroutines.flow.FlowKt__ErrorsKt\$retryWhen\$\$inlined\$unsafeFlow\$1.collect" -> SHRINK
    "kotlinx.coroutines.flow.FlowKt__ErrorsKt\$catch\$\$inlined\$unsafeFlow\$1.collect" -> SHRINK

    // kotlinx.coroutines.flow.internal
    "kotlinx.coroutines.flow.internal.FlowCoroutineKt\$scopedFlow\$1\$1.invokeSuspend" -> OMIT
    "kotlinx.coroutines.flow.internal.ChannelFlow\$collect\$2.invokeSuspend" -> OMIT
    "kotlinx.coroutines.flow.internal.ChannelFlow\$collectToFun\$1.invokeSuspend" -> OMIT
    "kotlinx.coroutines.flow.internal.CombineKt\$combineInternal\$2.invokeSuspend" -> SHRINK
    "kotlinx.coroutines.flow.internal.CombineKt\$combineInternal\$2\$1.invokeSuspend" -> SHRINK
    "kotlinx.coroutines.flow.internal.ChannelFlowTransformLatest\$flowCollect\$3.invokeSuspend" -> SHRINK

    // other in kotlinx.coroutines
    "kotlinx.coroutines.DelayKt.awaitCancellation" -> OMIT

    else -> KEEP
  }
}