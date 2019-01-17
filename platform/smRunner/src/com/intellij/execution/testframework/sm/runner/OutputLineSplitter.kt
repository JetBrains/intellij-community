/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.testframework.sm.runner

import com.intellij.execution.impl.ConsoleBuffer
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage

import java.util.ArrayList


abstract class OutputLineSplitter(private val myStdinSupportEnabled: Boolean) {

  private val myStdOutChunks = ArrayList<OutputChunk>()
  private val myStdErrChunks = ArrayList<OutputChunk>()
  private val mySystemChunks = ArrayList<OutputChunk>()
  private val myCurrentCyclicBufferSize = ConsoleBuffer.getCycleBufferSize()

  /**
   * @return if current stdout cache contains part of TC message.
   */
  protected val isInTeamcityMessage: Boolean
    get() = myStdOutChunks.stream().anyMatch { chunk -> chunk.text!!.startsWith(TEAMCITY_SERVICE_MESSAGE_PREFIX) }

  fun process(text: String, outputType: Key<*>) {
    var from = 0
    // new line char and teamcity message start are two reasons to flush previous line
    var newLineInd = text.indexOf(NEW_LINE.toInt())
    var teamcityMessageStartInd = text.indexOf(TEAMCITY_SERVICE_MESSAGE_PREFIX)
    while (from < text.length) {
      val nextFrom = Math.min(if (newLineInd != -1) newLineInd + 1 else text.length,
                              if (teamcityMessageStartInd != -1) teamcityMessageStartInd else text.length)
      val chunk = text.substring(from, nextFrom)
      processLine(chunk, outputType)
      from = nextFrom
      if (nextFrom == teamcityMessageStartInd) {
        flush() // Message may still go to buffer if it does not end with new line, force flush
        teamcityMessageStartInd = text.indexOf(TEAMCITY_SERVICE_MESSAGE_PREFIX, nextFrom + TEAMCITY_SERVICE_MESSAGE_PREFIX.length)
      }
      if (newLineInd != -1 && nextFrom == newLineInd + 1) {
        newLineInd = text.indexOf(NEW_LINE.toInt(), nextFrom)
      }
    }
  }

  private fun processLine(text: String, outputType: Key<*>) {
    if (text.isEmpty()) {
      return
    }
    if (ProcessOutputType.isStdout(outputType)) {
      processStdOutConsistently(text, outputType)
    }
    else {
      var chunksToFlush: List<OutputChunk>? = null
      val chunks = if (outputType === ProcessOutputTypes.SYSTEM) mySystemChunks else myStdErrChunks

      synchronized(chunks) {
        val lastChunk = ContainerUtil.getLastItem<OutputChunk, List<OutputChunk>>(chunks)
        if (lastChunk != null && outputType == lastChunk.key) {
          lastChunk.append(text)
        }
        else {
          chunks.add(OutputChunk(outputType, text))
        }
        if (StringUtil.endsWithChar(text, NEW_LINE)) {
          chunksToFlush = ArrayList(chunks)
          chunks.clear()
        }
      }
      if (chunksToFlush != null) {
        onChunksAvailable(chunksToFlush!!, false)
      }
    }
  }

  private fun processStdOutConsistently(text: String, outputType: Key<*>) {
    val textLength = text.length

    synchronized(myStdOutChunks) {
      myStdOutChunks.add(OutputChunk(outputType, text))
    }

    val lastChar = text[textLength - 1]
    if (lastChar == '\n' || lastChar == '\r') {
      // buffer contains consistent string
      flushStdOutBuffer()
    }
    else {
      // test framework may show some promt and ask user for smth. Question may not
      // finish with \n or \r thus buffer wont be flushed and user will have to input smth
      // before question. And question will became visible with next portion of text.
      // Such behaviour is confusing. So
      // 1. Let's assume that sevice messages starts with \n if console is editable
      // 2. Then we can suggest that each service message will start from new line and buffer should
      //    be flushed before every service message. Thus if chunks list is empty and output doesn't end
      //    with \n or \r but starts with ##teamcity then it is a service message and should be buffered otherwise
      //    we can safely flush buffer.

      // TODO if editable:
      if (myStdinSupportEnabled && !isInTeamcityMessage) {
        // We should not flush in the middle of TC message because of [PY-7659]
        flushStdOutBuffer()
      }
    }
  }

  private fun flushStdOutBuffer() {
    // if osColoredProcessHandler was attached it can split string with several colors
    // in several  parts. Thus '\n' symbol may be send as one part with some color
    // such situation should differ from single '\n' from process that is used by TC reporters
    // to separate TC commands from other stuff + optimize flushing
    // TODO: probably in IDEA mode such runners shouldn't add explicit \n because we can
    // successfully process broken messages across several flushes
    // size of parts may tell us either \n was single in original flushed data or it was
    // separated by process handler
    val chunks = ArrayList<OutputChunk>()
    var lastChunk: OutputChunk? = null
    synchronized(myStdOutChunks) {
      for (chunk in myStdOutChunks) {
        if (lastChunk != null && chunk.key === lastChunk!!.key) {
          val chunkText = chunk.text
          if (USE_CYCLE_BUFFER) {
            val builder = lastChunk!!.myBuilder
            if (builder != null &&
                builder.length + chunkText!!.length > myCurrentCyclicBufferSize &&
                myCurrentCyclicBufferSize > 2 * SM_MESSAGE_PREFIX) {
              builder.delete(SM_MESSAGE_PREFIX, Math.min(builder.length, myCurrentCyclicBufferSize - SM_MESSAGE_PREFIX))
            }
          }
          lastChunk!!.append(chunkText)
        }
        else {
          lastChunk = chunk
          chunks.add(chunk)
        }
      }

      myStdOutChunks.clear()
    }
    onChunksAvailable(chunks, chunks.size == 1)
  }

  fun flush() {
    flushStdOutBuffer()

    val stderrChunksToFlush: List<OutputChunk>
    synchronized(myStdErrChunks) {
      stderrChunksToFlush = ArrayList(myStdErrChunks)
      myStdErrChunks.clear()
    }
    onChunksAvailable(stderrChunksToFlush, false)

    val systemChunksToFlush: List<OutputChunk>
    synchronized(mySystemChunks) {
      systemChunksToFlush = ArrayList(mySystemChunks)
      mySystemChunks.clear()
    }
    onChunksAvailable(systemChunksToFlush, false)
  }

  private fun onChunksAvailable(chunks: List<OutputChunk>, tcLikeFakeOutput: Boolean) {
    for (chunk in chunks) {
      onLineAvailable(chunk.text!!, chunk.key, tcLikeFakeOutput)
    }
  }

  protected abstract fun onLineAvailable(text: String, outputType: Key<*>, tcLikeFakeOutput: Boolean)

  private class OutputChunk private constructor(val key: Key<*>, private var myText: String?) {
    private var myBuilder: StringBuilder? = null

    val text: String?
      get() {
        if (myBuilder != null) {
          myText = myBuilder!!.toString()
          myBuilder = null
        }
        return myText
      }

    fun append(text: String?) {
      if (myBuilder == null) {
        myBuilder = StringBuilder(myText!!)
        myText = null
      }
      myBuilder!!.append(text)
    }
  }

  companion object {
    val SM_MESSAGE_PREFIX = 105

    private val USE_CYCLE_BUFFER = ConsoleBuffer.useCycleBuffer()
    private val TEAMCITY_SERVICE_MESSAGE_PREFIX = ServiceMessage.SERVICE_MESSAGE_START
    private val NEW_LINE = '\n'
  }
}
