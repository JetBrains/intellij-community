// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner

import com.intellij.execution.impl.ConsoleBuffer
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import java.util.concurrent.atomic.AtomicReference


/**
 * External test runner sends plain text along with service messages ([ServiceMessage]) to [process].
 * Test runner sends data as stream and flushes it periodically. On each flush [process] is called.
 * That means [ServiceMessage] may be [process]ed in the middle.
 *
 * This class handles such cases by buffering service messages.
 * It then calls [onTextAvailable] for text or message. It is guaranteed that each call of [onTextAvailable] either contains
 * plain text or begins with [ServiceMessage] and ends with "\n".
 *
 * Current implementation supports only [ServiceMessage]s that end with new line.
 *
 * After process ends, call [flush] to process remain.
 *
 * Class is not thread safe in that matter that you can't call [process] for same stream (i.e. stderr) from different threads,
 * but [flush] could be called from any thread.
 *
 * If [bufferTextUntilNewLine] is set, any output (including service messages) is buffered until newline arrives.
 * Otherwise, this is done only for service messages.
 * It is recommended not to enable [bufferTextUntilNewLine] because it gives user ability to see text as fast as possible.
 * In some cases like when there is a separate protocol exists on top of text message that does not support messages
 * flushed in random places, this option must be enabled
 *
 */
abstract class OutputEventSplitter(private val bufferTextUntilNewLine: Boolean = false) {

  private val currentCyclicBufferSize = ConsoleBuffer.getCycleBufferSize()


  /**
   * [outputType] could be one of [ProcessOutputTypes] or any other [ProcessOutputType].
   * Only stdout ([ProcessOutputType.isStdout]) accepts Teamcity Messages ([ServiceMessage]).
   *
   * Stderr and System are flushed automatically, Stdout may be buffered until the end of the message.
   * Make sure you do not process same type from different threads.
   */
  fun process(text: String, outputType: Key<*>) {
    if (outputType is ProcessOutputType && outputType.isStdout) {
      processStdOut(text, outputType)
    }
    else {
      // Everything but stdout
      onTextAvailable(text, outputType)
    }
  }

  /**
   * For stderr and system [text] is provided as fast as possible.
   * For stdout [text] is either TC message that starts from [ServiceMessage.SERVICE_MESSAGE_START] and ends with new line
   * or chunk of process output
   * */
  abstract fun onTextAvailable(text: String, outputType: Key<*>)

  private val prevStdOutRef: AtomicReference<Output> = AtomicReference()

  private fun processStdOut(text: String, outputType: Key<*>) {
    var mergedText = text
    prevStdOutRef.getAndSet(null)?.let {
      if (it.outputType == outputType) {
        mergedText = it.text + text
      }
      else {
        flushStdOut(it.text, it.outputType)
      }
    }
    doProcessStdOut(mergedText, outputType)?.let {
      prevStdOutRef.set(Output(it, outputType))
    }
  }

  private fun doProcessStdOut(text: String, outputType: Key<*>): String? {
    var from = 0
    // new line char and teamcity message start are two reasons to flush previous text
    var newLineInd = text.indexOf(NEW_LINE)
    var teamcityMessageStartInd = text.indexOf(SERVICE_MESSAGE_START)
    var serviceMessageStarted = false
    while (from < text.length) {
      val nextFrom = Math.min(if (newLineInd != -1) newLineInd + 1 else Integer.MAX_VALUE,
                              if (teamcityMessageStartInd != -1) teamcityMessageStartInd else Integer.MAX_VALUE)
      if (nextFrom == Integer.MAX_VALUE) {
        break
      }
      if (from < nextFrom) {
        flushStdOut(text.substring(from, nextFrom), outputType)
      }
      from = nextFrom
      serviceMessageStarted = nextFrom == teamcityMessageStartInd
      if (serviceMessageStarted) {
        teamcityMessageStartInd = text.indexOf(SERVICE_MESSAGE_START, nextFrom + SERVICE_MESSAGE_START.length)
      }
      if (newLineInd != -1 && nextFrom == newLineInd + 1) {
        newLineInd = text.indexOf(NEW_LINE, nextFrom)
      }
    }
    if (from < text.length) {
      val unprocessed = text.substring(from)
      if (serviceMessageStarted) {
        return unprocessed
      }
      val preserveSuffixLength = if (bufferTextUntilNewLine) unprocessed.length else findSuffixLengthToPreserve(unprocessed)
      if (preserveSuffixLength < unprocessed.length) {
        flushStdOut(unprocessed.substring(0, unprocessed.length - preserveSuffixLength), outputType)
      }
      if (preserveSuffixLength > 0) {
        return unprocessed.substring(unprocessed.length - preserveSuffixLength)
      }
    }
    return null
  }

  private fun findSuffixLengthToPreserve(text: String): Int {
    for (suffixSize in SERVICE_MESSAGE_START.length - 1 downTo 1) {
      if (text.regionMatches(text.length - suffixSize, SERVICE_MESSAGE_START, 0, suffixSize)) {
        return suffixSize
      }
    }
    return 0
  }

  private data class Output(val text: String, val outputType: Key<*>)

  /**
   * Flush remainder. Call as last step.
   */
  fun flush() {
    prevStdOutRef.getAndSet(null)?.let {
      flushStdOut(it.text, it.outputType)
    }
  }

  private fun flushStdOut(text: String, key: Key<*>) {
    var result = text
    // Cut long lines
    if (USE_CYCLE_BUFFER &&
        text.length > currentCyclicBufferSize &&
        currentCyclicBufferSize > 2 * SM_MESSAGE_PREFIX) {
      result = text.substring(0, SM_MESSAGE_PREFIX) + text.substring(text.length - SM_MESSAGE_PREFIX)
    }

    onTextAvailable(result, key)
  }
}


internal const val SM_MESSAGE_PREFIX = 105

private val USE_CYCLE_BUFFER = ConsoleBuffer.useCycleBuffer()
private const val SERVICE_MESSAGE_START: String = ServiceMessage.SERVICE_MESSAGE_START
private const val NEW_LINE: Char = '\n'
