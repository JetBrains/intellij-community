// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner

import com.intellij.execution.impl.ConsoleBuffer
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import com.intellij.smRunner.OutputEventSplitterBase
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage

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
                                   private val cutNewLineBeforeServiceMessage: Boolean = false) :
  OutputEventSplitterBase<ProcessOutputType>(ServiceMessage.SERVICE_MESSAGE_START, bufferTextUntilNewLine, cutNewLineBeforeServiceMessage) {

  companion object {
    private val USE_CYCLE_BUFFER = ConsoleBuffer.useCycleBuffer()

    private val stdout = OutputType(ProcessOutputType.STDOUT, OutputStreamType.STDOUT)
    private val stderr = OutputType(ProcessOutputType.STDERR, OutputStreamType.STDERR)
    private val system = OutputType(ProcessOutputType.SYSTEM, OutputStreamType.SYSTEM)
  }

  /**
   * [processOutputType] could be one of [ProcessOutputTypes] or any other [ProcessOutputType].
   * Only stdout ([ProcessOutputType.isStdout]) accepts Teamcity Messages ([ServiceMessage]).
   *
   * Stderr and System are flushed automatically unless [bufferTextUntilNewLine],
   * Stdout may be buffered until the end of the message.
   * Make sure you do not process same type from different threads.
   */
  fun process(text: String, processOutputType: Key<*>) {
    val outputType = (processOutputType as? ProcessOutputType)?.toOutputType()
    if (outputType != null) {
      process(text, outputType)
    }
    else {
      onTextAvailableInternal(text, processOutputType)
    }
  }

  /**
   * For stderr and system [text] is provided as fast as possible unless [bufferTextUntilNewLine].
   * For stdout [text] is either TC message that starts from [ServiceMessage.SERVICE_MESSAGE_START] and ends with new line
   * or chunk of process output
   * */
  abstract fun onTextAvailable(text: String, outputType: Key<*>)

  final override fun onTextAvailable(text: String, outputType: OutputType<ProcessOutputType>) {
    onTextAvailableInternal(text, outputType.data)
  }

  private fun onTextAvailableInternal(text: String, processOutputType: Key<*>) {
    val textToAdd = if (USE_CYCLE_BUFFER) cutLineIfTooLong(text) else text
    onTextAvailable(textToAdd, processOutputType)
  }

  private fun ProcessOutputType.toOutputType(): OutputType<ProcessOutputType> {
    return when (this) {
      ProcessOutputType.STDOUT -> stdout
      ProcessOutputType.STDERR -> stderr
      ProcessOutputType.SYSTEM -> system
      else -> {
        val streamType = when (baseOutputType) {
          ProcessOutputType.STDOUT -> OutputStreamType.STDOUT
          ProcessOutputType.STDERR -> OutputStreamType.STDERR
          ProcessOutputType.SYSTEM -> OutputStreamType.SYSTEM
          else -> OutputStreamType.STDOUT
        }
        OutputType(this, streamType)
      }
    }
  }
}