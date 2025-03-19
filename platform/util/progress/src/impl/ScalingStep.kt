// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.progress.StepState
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicInteger

internal class ScalingStep(
  parentScope: CoroutineScope,
  val size: Int,
  private val state: MutableStateFlow<StepState>,
  private val config: StepConfig,
) {

  init {
    require(size >= 0)
  }

  private val cs = parentScope.childScope()
  private val childCounter = AtomicInteger()
  private val childUpdates = Channel<ChildUpdate>() // poor man's merge of a dynamic number of flows

  init {
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        state.emitAll(stateActor())
      }
      finally {
        state.value = doneState
      }
    }
  }

  fun cancel() {
    cs.cancel()
  }

  val allocatedWork: Int get() = _allocatedWork.get()

  private val _allocatedWork = AtomicInteger()

  private fun allocate(workSize: Int): Boolean {
    if (workSize < 0 || workSize > size) {
      LOG.error(IllegalArgumentException("Work size is expected to be a value in [0; $size], got $workSize"))
      return false
    }
    if (workSize == 0) {
      return true
    }
    while (true) {
      val value = _allocatedWork.get()
      val newValue = value + workSize
      if (newValue > size) {
        // overflow
        LOG.error(IllegalStateException("Total size of all steps must not exceed size of this reporter: $size"))
        return false
      }
      if (_allocatedWork.compareAndSet(value, newValue)) {
        return true
      }
    }
  }

  interface ChildHandle : AutoCloseable {

    val step: ProgressStep
  }

  fun createChild(workSize: Int, text: ProgressText?): ChildHandle {
    val effectiveSize = if (allocate(workSize)) {
      workSize
    }
    else {
      0
    }
    return doCreateChild(effectiveSize, text)
  }

  private fun doCreateChild(workSize: Int, text: ProgressText?): ChildHandle {
    val childConfig = config.childConfig(workSize == 0, text != null)
    val childStep: ProgressStep = if (childConfig == null) {
      EmptyProgressStep
    }
    else {
      ProgressStepImpl(cs, childConfig)
    }
    val updates = if (text != null && config.textLevel > 0) {
      // this step reports text
      childStep.progressUpdates().pushTextToDetails(text)
    }
    else {
      childStep.progressUpdates()
    }
    val childIndex = childCounter.getAndIncrement()

    @OptIn(ExperimentalCoroutinesApi::class)
    val subscription = cs.produce(capacity = Channel.CONFLATED) { // CONFLATED means we only receive latest update from child
      updates.collect {
        send(ChildUpdate(childIndex, workSize, it))
      }
    }
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        for (update in subscription) {
          childUpdates.send(update)
        }
      }
      finally {
        childUpdates.send(ChildUpdate(childIndex, workSize, null))
      }
    }
    return object : ChildHandle {
      override val step: ProgressStep = childStep
      override fun close(): Unit = subscription.cancel()
    }
  }

  /**
   * @param stepState `null` means last update
   */
  private data class ChildUpdate(val index: Int, val workSize: Int, val stepState: StepState?)

  private fun stateActor(): Flow<StepState> = flow {
    val childStates = Int2ObjectLinkedOpenHashMap<StepState>()
    var effectiveFraction: Double? = null
    var effectiveText: EffectiveText? = null

    for (childUpdate in childUpdates) {
      val (index, workSize, stepState) = childUpdate
      if (stepState != null) {
        val previousState = childStates.putAndMoveToFirst(index, stepState)
        if (!config.isIndeterminate && workSize != 0) {
          val fractionDelta = when {
            stepState.fraction != null -> {
              // new fraction is not indeterminate
              stepState.fraction - (previousState?.fraction ?: 0.0)
            }
            previousState?.fraction != null -> {
              // new fraction is indeterminate, but previous was not => fraction went back (due to raw reporting)
              -previousState.fraction
            }
            else -> {
              // this is a beginning of a new determinate child,
              // or indeterminate stage of determinate child
              // => don't change fraction OR set it to 0.0
              0.0
            }
          }
          effectiveFraction = (effectiveFraction ?: 0.0) + fractionDelta * workSize
        }
        if (config.textLevel > 0) {
          if (preferLatestTextUpdate(effectiveText?.textState, stepState)) {
            effectiveText = EffectiveText(index, stepState)
          }
          else if (effectiveText?.childIndex == index) {
            // new latest text is "worse", try to find a better one
            effectiveText = findEffectiveText(childStates)
          }
        }
      }
      else {
        val previousState = childStates.remove(index)
        if (!config.isIndeterminate && workSize != 0) {
          val fractionDelta = 1.0 - (previousState?.fraction ?: 0.0)
          effectiveFraction = (effectiveFraction ?: 0.0) + fractionDelta * workSize
        }
        if (config.textLevel > 0) {
          if (effectiveText != null && effectiveText.childIndex == index) {
            // This means that we are finishing a child which text is currently used as text of this step
            // => need to clear this text and replace it with previously reported text of a still active child.
            effectiveText = findEffectiveText(childStates)
          }
        }
      }
      //if (config.isIndeterminate) {
      //  check(effectiveFraction == null)
      //}
      //if (config.textLevel < 2) {
      //  check(effectiveText?.textState?.details == null)
      //}
      //if (config.textLevel < 1) {
      //  check(effectiveText?.textState?.text == null)
      //}
      emit(StepState(
        fraction = effectiveFraction?.div(size),
        text = effectiveText?.textState?.text,
        details = effectiveText?.textState?.details
      ))
    }
  }

  private data class EffectiveText(val childIndex: Int, val textState: StepState)

  private fun preferLatestTextUpdate(previous: StepState?, latest: StepState): Boolean {
    if (previous == null) {
      // don't have any text => prefer latest update
      return true
    }
    if (latest.text != null) {
      // latest has text => don't care about previous and use latest
      return true
    }
    if (config.textLevel > 1) { // this step reports details
      if (previous.text == null && latest.details != null) {
        return true
      }
    }
    return false
  }

  /**
   * Returns first state where [StepState.text] is present, otherwise returns first state where [StepState.details] is present.
   */
  private fun findEffectiveText(childStates: Int2ObjectLinkedOpenHashMap<StepState>): EffectiveText? {
    if (childStates.isEmpty()) {
      return null
    }
    var resultSoFar: EffectiveText? = null
    // Here we rely on update order (Int2ObjectLinkedOpenHashMap.putAndMoveToFirst) to find previous text
    val iterator = childStates.int2ObjectEntrySet().fastIterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      val state = entry.value
      if (state.text != null) {
        return EffectiveText(entry.intKey, state)
      }
      if (config.textLevel > 1 && state.details != null) {
        resultSoFar = EffectiveText(entry.intKey, state)
      }
    }
    return resultSoFar
  }
}
