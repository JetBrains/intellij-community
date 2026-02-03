// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object ConsoleLog {
  private val LOG = Logger.getInstance(ConsoleLog::class.java)

  fun info(message: String) {
    LOG.info(message)
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      if (isDifferentWithLastOne(message)) {
        println(message)
      }
    }
  }

  private val lastMessage: AtomicReference<Pair<String, Long>?> = AtomicReference(null)

  private fun isDifferentWithLastOne(message: String): Boolean {
    while (true) {
      val pair = lastMessage.get()
      val now = System.nanoTime()
      if (pair == null || pair.first != message || TimeUnit.NANOSECONDS.toMillis(now - pair.second) > 1000) {
        if (lastMessage.compareAndSet(pair, Pair(message, now))) {
          return true
        }
        else {
          continue
        }
      }
      return false
    }
  }

  fun warn(message: String) {
    LOG.warn(message)
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      println("WARN - $message")
    }
  }

  fun error(message: String, cause: Throwable? = null) {
    LOG.error(message, cause)
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      println("ERROR - $message")
      cause?.printStackTrace()
    }
  }
}