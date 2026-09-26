// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise

internal enum class IdeActivityState { NOT_STARTED, STARTED, FINISHED }

@ApiStatus.Internal
interface IdeActivity {
  val id: Int
  val stepId: Int
  val startedTimestamp: Long

  fun isFinished(): Boolean
  fun started(): IdeActivity
  fun started(dataSupplier: (() -> List<EventPair<*>>)?): IdeActivity
  fun startedAsync(dataSupplier: () -> Promise<List<EventPair<*>>>): IdeActivity
  fun stageStarted(stage: VarargEventId): IdeActivity
  fun stageStarted(stage: VarargEventId, dataSupplier: (() -> List<EventPair<*>>)? = null): IdeActivity
  fun finished(): IdeActivity
  fun finished(dataSupplier: (() -> List<EventPair<*>>)?): IdeActivity
}

@ApiStatus.Internal
class AutoClosableIdeActivity(
  private val activity: IdeActivity,
  private val closeAction: IdeActivity.() -> Unit
) : IdeActivity by activity, AutoCloseable {
  override fun close(): Unit = this.closeAction()
}