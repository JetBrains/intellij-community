// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui.model

import com.intellij.util.EventDispatcher
import com.intellij.util.containers.SortedList
import org.jetbrains.plugins.github.pullrequest.data.model.GithubPullRequestFileCommentsThreadMapping
import java.util.*

class GithubPullRequestFileCommentsThreadsMap {
  private val changeEventDispatcher = EventDispatcher.create(ChangesListener::class.java)

  val threadsByLine: MutableMap<Int, SortedList<GithubPullRequestFileCommentsThread>> = mutableMapOf()

  fun addChangesListener(listener: ChangesListener) = changeEventDispatcher.addListener(listener)

  fun update(mappingsByLine: Map<Int, List<GithubPullRequestFileCommentsThreadMapping>>) {
    val removedLines = threadsByLine.keys - mappingsByLine.keys
    for (line in removedLines) {
      val removed = threadsByLine.remove(line).orEmpty()
      changeEventDispatcher.multicaster.threadsRemoved(line, removed.toList())
    }

    for ((line, mappings) in mappingsByLine) {
      val threads = threadsByLine.computeIfAbsent(line) { SortedList(compareBy { it.firstCommentCreated }) }

      val threadsById = threads.map { it.firstCommentId to it }.toMap()
      val mappingsById = mappings.map { it.comments.first().id to it }.toMap()
      val removedThreads = (threadsById - mappingsById.keys).values
      if (removedThreads.isNotEmpty()) {
        threads.removeAll(removedThreads)
        changeEventDispatcher.multicaster.threadsRemoved(line, removedThreads.toList())
      }

      val addedThreads = mutableListOf<GithubPullRequestFileCommentsThread>()
      for ((id, mapping) in mappingsById) {
        val current = threadsById[id]
        if (current == null) {
          val thread = GithubPullRequestFileCommentsThread.convert(mapping)
          threads.add(thread)
          addedThreads.add(thread)
        }
        else {
          current.update(mapping.comments)
        }
      }
      if (addedThreads.isNotEmpty()) changeEventDispatcher.multicaster.threadsAdded(line, addedThreads)
    }
  }

  interface ChangesListener : EventListener {
    fun threadsAdded(line: Int, threads: List<GithubPullRequestFileCommentsThread>)
    fun threadsRemoved(line: Int, threads: List<GithubPullRequestFileCommentsThread>)
  }
}