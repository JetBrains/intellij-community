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
 * If [bufferTextUntilNewLine] is set, any output/err (including service messages) is buffered until newline arrives.
 * Otherwise, this is done only for service messages.
 * It is recommended not to enable [bufferTextUntilNewLine] because it gives user ability to see text as fast as possible.
 * In some cases like when there is a separate protocol exists on top of text message that does not support messages
 * flushed in random places, this option must be enabled.
 *
 * If [cutNewLineBeforeServiceMessage] is set, each service message must have "\n" prefix which is cut.
 *
 */
abstract class OutputEventSplitter(private val bufferTextUntilNewLine: Boolean = false,
                                   private val cutNewLineBeforeServiceMessage: Boolean = false) {

  private val prevRefs: Map<ProcessOutputType, AtomicReference<Output>> =
    listOf(ProcessOutputType.STDOUT, ProcessOutputType.STDERR, ProcessOutputType.SYSTEM)
      .map { it to AtomicReference<Output>() }.toMap()

  private val ProcessOutputType.prevRef get() = prevRefs[baseOutputType.baseOutputType]
  private var newLinePending = false


  /**
   * [outputType] could be one of [ProcessOutputTypes] or any other [ProcessOutputType].
   * Only stdout ([ProcessOutputType.isStdout]) accepts Teamcity Messages ([ServiceMessage]).
   *
   * Stderr and System are flushed automatically unless [bufferTextUntilNewLine],
   * Stdout may be buffered until the end of the message.
   * Make sure you do not process same type from different threads.
   */
  fun process(text: String, outputType: Key<*>) {
    val prevRef = (outputType as? ProcessOutputType)?.prevRef
    if (prevRef == null) {
      flushInternal(text, outputType)
      return
    }

    var mergedText = text
    prevRef.getAndSet(null)?.let {
      if (it.outputType == outputType) {
        mergedText = it.text + text
      }
      else {
        flushInternal(it.text, it.outputType)
      }
    }
    processInternal(mergedText, outputType)?.let {
      prevRef.set(Output(it, outputType))
    }
  }

  /**
   * For stderr and system [text] is provided as fast as possible unless [bufferTextUntilNewLine].
   * For stdout [text] is either TC message that starts from [ServiceMessage.SERVICE_MESSAGE_START] and ends with new line
   * or chunk of process output
   * */
  abstract fun onTextAvailable(text: String, outputType: Key<*>)

  private fun processInternal(text: String, outputType: ProcessOutputType): String? {
    var from = 0
    val processServiceMessages = outputType.isStdout
    // new line char and teamcity message start are two reasons to flush previous text
    var newLineInd = text.indexOf(NEW_LINE)
    var teamcityMessageStartInd = if (processServiceMessages) text.indexOf(SERVICE_MESSAGE_START) else -1
    var serviceMessageStarted = false
    while (from < text.length) {
      val nextFrom = Math.min(if (newLineInd != -1) newLineInd + 1 else Integer.MAX_VALUE,
                              if (teamcityMessageStartInd != -1) teamcityMessageStartInd else Integer.MAX_VALUE)
      if (nextFrom == Integer.MAX_VALUE) {
        break
      }
      if (from < nextFrom) {
        flushInternal(text.substring(from, nextFrom), outputType)
      }
      assert(from != nextFrom || from == 0) {"``from`` is $from and it hasn't been changed since last check. Loop is frozen"}
      from = nextFrom
      serviceMessageStarted = processServiceMessages && nextFrom == teamcityMessageStartInd
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
      val preserveSuffixLength = when {
        bufferTextUntilNewLine -> unprocessed.length
        processServiceMessages -> findSuffixLengthToPreserve(unprocessed)
        else -> 0
      }
      if (preserveSuffixLength < unprocessed.length) {
        flushInternal(unprocessed.substring(0, unprocessed.length - preserveSuffixLength), outputType)
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

  private data class Output(val text: String, val outputType: ProcessOutputType)

  /**
   * Flush remainder. Call as last step.
   */
  fun flush() {
    prevRefs.values.forEach { reference ->
      reference.getAndSet(null)?.let {
        flushInternal(it.text, it.outputType, lastFlush = true)
      }
    }
  }

  private fun flushInternal(text: String, key: Key<*>, lastFlush: Boolean = false) {
    if (cutNewLineBeforeServiceMessage && key is ProcessOutputType && key.isStdout) {
      if (newLinePending) { //Prev. flush was "\n".
        if (!text.startsWith(SERVICE_MESSAGE_START) || (lastFlush)) {
          onTextAvailable("\n", key)
        }
        newLinePending = false
      }
      if (text == "\n" && !lastFlush) {
        newLinePending = true
        return
      }
    }

    val textToAdd = if (USE_CYCLE_BUFFER) cutLineIfTooLong(text) else text
    onTextAvailable(textToAdd, key)
  }
}

private val USE_CYCLE_BUFFER = ConsoleBuffer.useCycleBuffer()
private const val SERVICE_MESSAGE_START: String = ServiceMessage.SERVICE_MESSAGE_START
private const val NEW_LINE: Char = '\n'
