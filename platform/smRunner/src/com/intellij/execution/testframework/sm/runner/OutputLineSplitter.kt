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
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage


/**
 * Processes text from test framework streams and runs [onTextAvailable] when consistency is guaranteed.
 * Class is not thread safe in that matter that you can't call [process] for same stream (i.e. stderr) from different threads,
 * but [flush] could be called from any thread.
 *
 * See [process] for more info
 *
 */
abstract class OutputLineSplitter() {

  @Suppress("UNUSED_PARAMETER") // For backward compatibility
  @Deprecated(message = "Output is flushed automatically when possible")
  constructor(ignored: Boolean) : this()

  private val currentCyclicBufferSize = ConsoleBuffer.getCycleBufferSize()

  private val tcMessagesManager = StdOutTcMessagesProcessor()

  /**
   * [outputType] could be one of [ProcessOutputTypes] or any other [ProcessOutputType].
   * Only stdout ([ProcessOutputType.isStdout]) accepts Teamcity Messages ([ServiceMessage]).
   *
   * Stderr and System are flushed automatically, Stdout may be buffered until the end of the message.
   * Make sure you do not process same type from different threads.
   */
  fun process(text: String, outputType: Key<*>) {
    if (outputType is ProcessOutputType && outputType.isStdout) {
      // Synced because flush may be called from different thread
      flushToStdOut(synchronized(tcMessagesManager) {
        return@synchronized tcMessagesManager.processStdout(text, outputType)
      })
    }
    else {
      // Everything but stdout
      onTextAvailable(text, outputType)
    }
  }


  /**
   * Flush remainder. Call as last step.
   */
  fun flush() {
    flushToStdOut(synchronized(tcMessagesManager) {
      return@synchronized tcMessagesManager.popAllChunks()
    })
  }

  private fun flushToStdOut(chunks: List<OutputChunk>) {
    chunks.forEach { chunk ->
      val builder = chunk.builder

      // Cut long lines
      if (USE_CYCLE_BUFFER &&
          builder.length > currentCyclicBufferSize &&
          currentCyclicBufferSize > 2 * SM_MESSAGE_PREFIX) {
        builder.delete(SM_MESSAGE_PREFIX, Math.min(builder.length, currentCyclicBufferSize - SM_MESSAGE_PREFIX))
      }

      val chunkText = builder.toString()
      onTextAvailable(chunkText, chunk.key)
    }
  }

  /**
   * For stderr and system [text] is provided as fast as possible.
   * For stdout [text] is either TC message that starts from [ServiceMessage.SERVICE_MESSAGE_START] and ends with new line
   * or chunk of process output
   */
  protected open fun onTextAvailable(text: String, outputType: Key<*>) {
    @Suppress("DEPRECATION") //For backward compatibility
    onLineAvailable(text, outputType, false)
  }

  @Deprecated(
    message = "Use onTextAvailable instead, will be removed in 2020",
    replaceWith = ReplaceWith("onTextAvailable(text, outputType)"))
  protected open fun onLineAvailable(text: String, outputType: Key<*>, tcLikeFakeOutput: Boolean) {
    throw NotImplementedError("Use onTextAvailable instead")
  }
}


const val SM_MESSAGE_PREFIX = 105

private val USE_CYCLE_BUFFER = ConsoleBuffer.useCycleBuffer()
private const val TC_MESSAGE_PREFIX = ServiceMessage.SERVICE_MESSAGE_START
private const val TC_MESSAGE_LAST_CHAR_INDEX = TC_MESSAGE_PREFIX.length - 1
private val TC_MESSAGE_FIRST_CHAR = TC_MESSAGE_PREFIX[0]


/**
 * Process stdout char-by-char ([processStdout]) to guarantee tc messages are consistent.
 * Result is returned by [popAllChunks] which is a list of [OutputChunk]. Each chunk is plaintext or TC service message.
 *
 * May be in the following states:
 * * outside of TC message
 * * inside of [TC_MESSAGE_PREFIX] (#(here)#team for example): [insideOfPrefixIndices] is not null in this case
 * * inside of message itself (between [TC_MESSAGE_PREFIX] and new line): [insideOfTcMessage] is true
 */
private class StdOutTcMessagesProcessor {
  private var insideOfPrefixIndices: InsideOfPrefixIndices? = null
  private val mayBeInTcPrefix get() = insideOfPrefixIndices != null
  private var insideOfTcMessage = false
  private val chunksCollection = ChunksCollection()

  /**
   * [text] may have different [outputType] but it must be [ProcessOutputType.isStdout].
   * [outputType] can't be changed inside of message.
   */
  fun processStdout(text: String, outputType: Key<*>): List<OutputChunk> {
    if (chunksCollection.changeTypeIfRequired(outputType)) {
      assert(!insideOfTcMessage) { "Can't change chunk type $outputType when in message" }
    }

    for (char in text) {
      chunksCollection.builder.append(char)
      val currentInsideOfMessagePrefix = insideOfPrefixIndices
      when {
        (char == '\n' && insideOfTcMessage) -> {
          // Message finished
          chunksCollection.addNextChunk()
          insideOfTcMessage = false
        }
        (!mayBeInTcPrefix && !insideOfTcMessage) -> {
          // Some random char, may be start of TC prefix
          startInsideOfPrefixIfNeeded(char)
        }
        (currentInsideOfMessagePrefix != null) -> {
          // We are inside of message prefix probably, promote index to next char
          val nextIndex = ++currentInsideOfMessagePrefix.indexInTcMessagePrefix
          if (char != TC_MESSAGE_PREFIX[nextIndex]) {
            // Next char is not part of prefix: it was not prefix, just some ## chars.
            insideOfPrefixIndices = null
            startInsideOfPrefixIfNeeded(char) //But is till can be first char of prefix
          }
          else if (nextIndex == TC_MESSAGE_LAST_CHAR_INDEX) {
            // Message prefix just finished, we are inside of message
            if (chunksCollection.builder.length != TC_MESSAGE_LAST_CHAR_INDEX + 1) {
              //Current chunk has something behind prefix. We need to cut this prefix, and create new chunk.
              // Chunk("foo ##teamcity[") --> Chunk("foo"), Chunk("##teamcity[")
              chunksCollection.builder.delete(insideOfPrefixIndices!!.indexOfPrefixStartInChunk, chunksCollection.builder.length + 1)
              chunksCollection.addNextChunk()
              chunksCollection.builder.append(TC_MESSAGE_PREFIX)
            }
            insideOfPrefixIndices = null
            insideOfTcMessage = true
          }
        }
      }
    }

    // If not inside of message and prefix then flush.
    // It could ne that text ends with something that looks like TC prefix. It will not be flushed here
    // but will be flushed explicitly on flush()
    return if (!insideOfTcMessage && insideOfPrefixIndices == null) {
      popAllChunks()
    }
    else {
      emptyList()
    }
  }

  private fun startInsideOfPrefixIfNeeded(char: Char) {
    if (char == TC_MESSAGE_FIRST_CHAR) {
      insideOfPrefixIndices = InsideOfPrefixIndices(indexOfPrefixStartInChunk = chunksCollection.builder.length - 1)
    }
  }

  fun popAllChunks() = chunksCollection.popAllChunks()
}

private class OutputChunk(val key: Key<*>) {
  val builder = StringBuilder()
  override fun toString() = builder.toString()

}

/**
 * When [StdOutTcMessagesProcessor] is inside of TC prefix, it has 2 indices:
 * * [indexOfPrefixStartInChunk] position of TC prefix start inside of chunk (will be used to cut it later).
 * Chunk("f##te") --> 0 in this case.
 *
 * * [indexInTcMessagePrefix] current position inside of [TC_MESSAGE_PREFIX]. "#(here)#mess" is 1.
 */
private data class InsideOfPrefixIndices(
  var indexOfPrefixStartInChunk: Int,
  var indexInTcMessagePrefix: Int = 0
)

/**
 * Stores collection of [chunks] providing access to [builder] of last chunk.
 * Schedule next chunk creation by calling [addNextChunk] (will be created in lazy manner).
 * First, chunk type must be set with [changeTypeIfRequired]
 *
 * Get result with [popAllChunks]
 */
private class ChunksCollection {

  private var nextChunk: Key<*>? = null
  private val chunks = mutableListOf<OutputChunk>()
  private val currentChunk: OutputChunk
    get() {
      createNextChunkIfRequired()
      return chunks.lastOrNull() ?: throw AssertionError("No last chunk. No ${::changeTypeIfRequired} nor ${::addNextChunk} called")
    }

  private fun createNextChunkIfRequired() {
    nextChunk?.let {
      chunks.add(OutputChunk(it))
      nextChunk = null
    }
  }

  /**
   * Use current chunk builder to modify chars
   */
  val builder get() = currentChunk.builder


  /**
   * returns true if type changed, false if type set first type
   */
  fun changeTypeIfRequired(type: Key<*>): Boolean {
    if (chunks.lastOrNull()?.key != type) {
      nextChunk = type
      return true
    }
    return false
  }

  fun addNextChunk() {
    nextChunk = currentChunk.key
  }


  fun popAllChunks(): List<OutputChunk> {
    val result = ArrayList(chunks)
    chunks.clear()
    return result
  }

}
