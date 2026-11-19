// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.ReadActionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.util.Disposer

/**
 * Runs [block] and counts the outermost read actions that the current thread takes in it.
 * The count ignores the read actions of other threads, for example of the indexing or of the VFS refresh.
 * [block] must not suspend, because a suspended coroutine can continue on a different thread.
 */
internal inline fun <T> countReadActions(block: () -> T): Pair<T, Int> {
  val counter = ReadActionCounter(Thread.currentThread())
  val disposable = Disposer.newDisposable()
  try {
    ApplicationManagerEx.getApplicationEx().addReadActionListener(counter, disposable)
    return block() to counter.count
  }
  finally {
    Disposer.dispose(disposable)
  }
}

@PublishedApi
internal class ReadActionCounter(private val thread: Thread) : ReadActionListener {
  var count: Int = 0
    private set
  private var depth = 0

  override fun readActionStarted(action: Class<*>) {
    if (Thread.currentThread() !== thread) return
    depth++
  }

  override fun readActionFinished(action: Class<*>) {
    if (Thread.currentThread() !== thread) return
    if (--depth == 0) count++
  }
}
