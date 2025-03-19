// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Predicates
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.vcs.log.util.VcsLogUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate

@ApiStatus.Internal
class CurrentBranchConditionCache(private val logData: VcsLogData, parent: Disposable) : Disposable {
  private val executor: ExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("VcsLog Current Branch Condition",
                                                                                               1)
  private val conditions: Map<VirtualFile, AtomicReference<ConditionHolder>> = logData.roots.associateWith {
    AtomicReference(ConditionHolder(Predicates.alwaysFalse(), State.OUTDATED))
  }

  init {
    Disposer.register(parent, this)
  }

  fun getContainedInCurrentBranchCondition(root: VirtualFile): Predicate<Int> {
    val holder = conditions[root] ?: return Predicates.alwaysFalse()

    val oldCondition = holder.get()
    if (oldCondition.isOutdated()) {
      val inProgress = oldCondition.inProgress()
      if (holder.compareAndSet(oldCondition, inProgress)) {
        executor.execute {
          if (holder.get() == inProgress) {
            holder.compareAndSet(inProgress, ConditionHolder(doGetContainedInCurrentBranchCondition(root), State.VALID))
          }
        }
      }
    }

    return holder.get().condition
  }

  private fun doGetContainedInCurrentBranchCondition(root: VirtualFile): Predicate<Int> {
    val dataPack = logData.dataPack
    if (dataPack === DataPack.EMPTY) return Predicates.alwaysFalse()

    try {
      val branchName = logData.getLogProvider(root).getCurrentBranch(root) ?: return Predicates.alwaysFalse()
      val branchRef = VcsLogUtil.findBranch(dataPack.refsModel, root, branchName) ?: return Predicates.alwaysFalse()
      val branchIndex = logData.getCommitIndex(branchRef.commitHash, branchRef.root)
      return dataPack.permanentGraph.getContainedInBranchCondition(setOf(branchIndex))
    }
    catch (e: ProcessCanceledException) {
      return Predicates.alwaysFalse()
    }
  }

  fun clear() {
    conditions.forEach { (_, holder) -> holder.updateAndGet { it.outdated() } }
  }

  override fun dispose() {
    executor.shutdownNow()
  }

  private data class ConditionHolder(val condition: Predicate<Int>, val state: State) {
    private fun withState(s: State) = if (state == s) this else ConditionHolder(condition, s)
    fun outdated() = withState(State.OUTDATED)
    fun inProgress() = withState(State.IN_PROGRESS)

    fun isOutdated() = state == State.OUTDATED
  }

  private enum class State {
    VALID,
    OUTDATED,
    IN_PROGRESS
  }
}
