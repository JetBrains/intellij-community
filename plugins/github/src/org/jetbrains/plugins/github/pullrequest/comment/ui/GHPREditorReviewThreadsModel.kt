// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.util.EventDispatcher
import com.intellij.util.containers.SortedList
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import java.util.*

class GHPREditorReviewThreadsModel {
  private val changeEventDispatcher = EventDispatcher.create(ChangesListener::class.java)

  val modelsByLine: MutableMap<Int, SortedList<GHPRReviewThreadModel>> = mutableMapOf()

  fun addChangesListener(listener: ChangesListener) = changeEventDispatcher.addListener(listener)

  fun update(threadsByLine: Map<Int, List<GHPullRequestReviewThread>>) {
    val removedLines = modelsByLine.keys - threadsByLine.keys
    for (line in removedLines) {
      val removed = modelsByLine.remove(line).orEmpty()
      changeEventDispatcher.multicaster.threadsRemoved(line, removed.toList())
    }

    for ((line, threads) in threadsByLine) {
      val models = modelsByLine.computeIfAbsent(line) { SortedList(compareBy { it.createdAt }) }

      val modelsById = models.map { it.id to it }.toMap()
      val threadsById = threads.map { it.id to it }.toMap()
      val removedModels = (modelsById - threadsById.keys).values
      if (removedModels.isNotEmpty()) {
        models.removeAll(removedModels)
        changeEventDispatcher.multicaster.threadsRemoved(line, removedModels.toList())
      }

      val addedModels = mutableListOf<GHPRReviewThreadModel>()
      for ((id, thread) in threadsById) {
        val current = modelsById[id]
        if (current == null) {
          val model = GHPRReviewThreadModelImpl(thread)
          models.add(model)
          addedModels.add(model)
        }
        else {
          current.update(thread)
        }
      }
      if (addedModels.isNotEmpty()) changeEventDispatcher.multicaster.threadsAdded(line, addedModels)
    }
  }

  interface ChangesListener : EventListener {
    fun threadsAdded(line: Int, threads: List<GHPRReviewThreadModel>)
    fun threadsRemoved(line: Int, threads: List<GHPRReviewThreadModel>)
  }
}