// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.smRunner

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
 */
abstract class OutputEventSplitterBase<T>(private val serviceMessagePrefix: String,
                                          private val bufferTextUntilNewLine: Boolean,
                                          private val cutNewLineBeforeServiceMessage: Boolean) {

  data class OutputType<T>(val data: T, val streamType: OutputStreamType)

  enum class OutputStreamType {
    STDOUT, STDERR, SYSTEM
  }

  private data class Output<T>(val text: String, val outputType: OutputType<T>)

  private var newLinePending = false

  private val prevRefs = OutputStreamType.values().associateWith { AtomicReference<Output<T>>() }

  /**
   * For stderr and system [text] is provided as fast as possible unless [bufferTextUntilNewLine].
   * For stdout [text] is either TC message that starts from [serviceMessagePrefix] and ends with new line
   * or chunk of process output
   * */
  abstract fun onTextAvailable(text: String, outputType: OutputType<T>)

  /**
   * Only stdout ([OutputType.streamType] == [OutputStreamType.STDOUT]) accepts Teamcity Messages ([ServiceMessage]).
   *
   * Stderr and System are flushed automatically unless [bufferTextUntilNewLine],
   * Stdout may be buffered until the end of the message.
   * Make sure you do not process same type from different threads.
   */
  fun process(text: String, outputType: OutputType<T>) {
    val prevRef = requireNotNull(prevRefs[outputType.streamType]) {
      "reference to ${outputType.streamType} stream type is missing"
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

  private fun processInternal(text: String, outputType: OutputType<T>): String? {
    var from = 0
    val processServiceMessages = outputType.streamType == OutputStreamType.STDOUT
    // new line char and teamcity message start are two reasons to flush previous text
    var newLineInd = text.indexOf('\n')
    var teamcityMessageStartInd = if (processServiceMessages) text.indexOf(serviceMessagePrefix) else -1
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
      assert(from != nextFrom || from == 0) { "``from`` is $from and it hasn't been changed since last check. Loop is frozen" }
      from = nextFrom
      serviceMessageStarted = processServiceMessages && nextFrom == teamcityMessageStartInd
      if (serviceMessageStarted) {
        teamcityMessageStartInd = text.indexOf(serviceMessagePrefix, nextFrom + serviceMessagePrefix.length)
      }
      if (newLineInd != -1 && nextFrom == newLineInd + 1) {
        newLineInd = text.indexOf('\n', nextFrom)
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
    for (suffixSize in serviceMessagePrefix.length - 1 downTo 1) {
      if (text.regionMatches(text.length - suffixSize, serviceMessagePrefix, 0, suffixSize)) {
        return suffixSize
      }
    }
    return 0
  }

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

  private fun flushInternal(text: String, outputType: OutputType<T>, lastFlush: Boolean = false) {
    if (cutNewLineBeforeServiceMessage && outputType.streamType == OutputStreamType.STDOUT) {
      if (newLinePending) { //Prev. flush was "\n".
        if (!text.startsWith(serviceMessagePrefix) || (lastFlush)) {
          onTextAvailable("\n", outputType)
        }
        newLinePending = false
      }
      if (text == "\n" && !lastFlush) {
        newLinePending = true
        return
      }
    }

    onTextAvailable(text, outputType)
  }
}
