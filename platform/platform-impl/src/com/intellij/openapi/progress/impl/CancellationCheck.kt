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
 * - additional flag `trackTrace` could be enabled by setting a registry key `ide.cancellation.check.trace.all` to `true`
 *   (default is false), which will make the checker attach the last cancellation check trace, id adds additional overhead
 *   but simplifies the results interpretation.
 */
class CancellationCheck private constructor(
  val thresholdMs: () -> Long,
  val checkEnabled: () -> Boolean,
  val trackTrace: () -> Boolean
) {

  @TestOnly
  internal constructor(thresholdMs: Long) : this(thresholdMs = { thresholdMs }, checkEnabled = { true }, trackTrace = { true })

  private val statusRecord = ThreadLocal.withInitial { CanceledStatusRecord() }

  private fun checkCancellationDiff(record: CanceledStatusRecord, failure: Throwable?) {
    if (record.enabled) {
      val now = Clock.getTime()
      val diff = now - record.timestamp
      if (diff > thresholdMs()) {
        val message = buildString {
          append("${Thread.currentThread().name} last checkCanceled was $diff ms ago")
          if (failure != null) append(" ($failure)")
        }
        val t = Throwable(message, record.lastCancellationCall.takeIf { trackTrace() })
        LOG.error(message, t)
      }
      record.timestamp = now
      if (trackTrace())
        record.lastCancellationCall = Exception("previous check cancellation call")
    }
  }

  fun <T> withCancellationCheck(block: () -> T): T {
    if (!checkEnabled()) return block()

    val record = statusRecord.get()
    if (record.enabled) return block()

    val hook = CoreProgressManager.CheckCanceledHook {
      checkCancellationDiff(statusRecord.get(), null)
      false
    }

    var r:T? = null
    (ProgressManager.getInstance() as ProgressManagerImpl).runWithHook(hook) {
      record.enabled = true
      record.timestamp = Clock.getTime()
      record.lastCancellationCall = null
      try {
        r = try {
          block()
        }
        catch (e: Throwable) {
          checkCancellationDiff(record, e)
          throw e
        }
        checkCancellationDiff(record, null)
      }
      finally {
        record.enabled = false
        record.timestamp = Clock.getTime()
        record.lastCancellationCall = null
      }
    }
    return r as T
  }

  private data class CanceledStatusRecord(
    var enabled: Boolean = false,
    var timestamp: Long = Clock.getTime(),
    var lastCancellationCall: Exception? = null
  )

  companion object {
    private val LOG = Logger.getInstance(CancellationCheck::class.java)

    @JvmStatic
    private val INSTANCE: CancellationCheck =
      CancellationCheck(
        thresholdMs = { Registry.intValue("ide.cancellation.check.threshold").toLong() },
        checkEnabled = { Registry.`is`("ide.cancellation.check.enabled") },
        trackTrace = { Registry.`is`("ide.cancellation.check.trace.all") }
      )

    @JvmStatic
    fun <T> runWithCancellationCheck(block: () -> T): T =
      INSTANCE.withCancellationCheck(block)
  }
}


