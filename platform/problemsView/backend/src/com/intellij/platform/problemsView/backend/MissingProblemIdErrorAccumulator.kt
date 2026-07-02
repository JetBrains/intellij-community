// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG = Logger.getInstance("com.intellij.platform.problemsView.backend.MissingProblemIdErrorAccumulator")

private const val FLUSH_THRESHOLD = 50

@Service(Service.Level.PROJECT)
internal class MissingProblemIdErrorAccumulator {

  private val messagesLock = ReentrantLock()
  private val messages = ArrayList<String>(FLUSH_THRESHOLD)

  fun record(message: String) {
    val batch = messagesLock.withLock {
      messages.add(message)
      if (messages.size < FLUSH_THRESHOLD) return
      val copy = ArrayList(messages)
      messages.clear()
      copy
    }
    flush(batch)
  }

  private fun flush(batch: List<String>) {
    val content = batch.mapIndexed { i, m -> "#${i + 1}\n$m" }.joinToString("\n")
    LOG.error(
      "Accumulated ${batch.size} 'Problem ID not found' exceptions. See details in missing-problem-id.txt",
      Attachment("missing-problem-id.txt", content),
    )
  }

  companion object {
    fun getInstance(project: Project): MissingProblemIdErrorAccumulator = project.service()
  }
}
