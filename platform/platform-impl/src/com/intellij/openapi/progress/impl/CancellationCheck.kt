// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Clock
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.TestOnly

/**
 * It is used to check if [ProgressManager.checkCanceled] is invoked often enough - at least once per a threshold ms.
 *
 * For global usage [CancellationCheck.runWithCancellationCheck] could be used:
 * - it has to be enabled with a registry key `ide.cancellation.check.enabled`, it is disabled by default
 * - threshold (in ms) is specified with a registry key `ide.cancellation.check.threshold`, default is 500
 */
class CancellationCheck private constructor(val thresholdMs: () -> Long, val checkEnabled: () -> Boolean) {

  @TestOnly
  internal constructor(thresholdMs: Long): this(thresholdMs = { thresholdMs }, checkEnabled = { true })

  private val statusRecord = ThreadLocal.withInitial { CanceledStatusRecord() }
  private val hook = CoreProgressManager.CheckCanceledHook {
    checkCancellationDiff(statusRecord.get())
    false
  }

  private fun checkCancellationDiff(record: CanceledStatusRecord) {
    if (record.enabled) {
      val now = Clock.getTime()
      val diff = now - record.timestamp
      if (diff > thresholdMs()) {
        LOG.error("${Thread.currentThread().name} last checkCanceled was $diff ms ago")
      }
      record.timestamp = now
    }
  }

  private fun enableCancellationTimer(record: CanceledStatusRecord, enabled: Boolean) {
    val progressManagerImpl = ProgressManager.getInstance() as ProgressManagerImpl

    if (enabled) progressManagerImpl.addCheckCanceledHook(hook) else progressManagerImpl.removeCheckCanceledHook(hook)
    record.enabled = enabled
    record.timestamp = Clock.getTime()
  }

  fun <T> withCancellationCheck(block: () -> T): T {
    if (!checkEnabled()) return block()

    val record = statusRecord.get()
    if (record.enabled) return block()

    enableCancellationTimer(record, true)
    try {
      return block()
    } finally {
      try {
        checkCancellationDiff(record)
      }
      finally {
        enableCancellationTimer(record,false)
      }
    }
  }

  private data class CanceledStatusRecord(var enabled: Boolean = false, var timestamp: Long = Clock.getTime())

  companion object {
    private val LOG = Logger.getInstance(CancellationCheck::class.java)

    @JvmStatic
    private val INSTANCE: CancellationCheck =
      CancellationCheck(
        thresholdMs = { Registry.intValue("ide.cancellation.check.threshold").toLong() },
        checkEnabled = { Registry.`is`("ide.cancellation.check.enabled") }
      )

    @JvmStatic
    fun <T> runWithCancellationCheck(block: () -> T): T =
      INSTANCE.withCancellationCheck(block)
  }
}


