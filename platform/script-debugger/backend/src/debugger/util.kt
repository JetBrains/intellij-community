/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.debugger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.CharSequenceBackedByChars
import com.intellij.util.io.addChannelListener
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import org.jetbrains.annotations.PropertyKey
import java.io.File
import java.io.FileOutputStream
import java.nio.CharBuffer
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentLinkedQueue

internal class LogEntry(val message: Any, val marker: String) {
  internal val time = System.currentTimeMillis()
}

class MessagingLogger internal constructor(private val queue: ConcurrentLinkedQueue<LogEntry>) {
  internal @Volatile var closed = false

  fun add(inMessage: CharSequence, marker: String = "IN") {
    queue.add(LogEntry(inMessage, marker))
  }

  fun add(outMessage: ByteBuf, marker: String = "OUT") {
    queue.add(LogEntry(outMessage.copy(), marker))
  }

  fun close() {
    closed = true
  }

  fun closeOnChannelClose(channel: Channel) {
    channel.closeFuture().addChannelListener {
      try {
        add("\"Closed\"", "Channel")
      }
      finally {
        close()
      }
    }
  }
}

fun createDebugLogger(@PropertyKey(resourceBundle = Registry.REGISTRY_BUNDLE) key: String, suffix: String = ""): MessagingLogger? {
  var debugFile = Registry.stringValue(key)
  if (debugFile.isNullOrEmpty()) {
    return null
  }

  if (!suffix.isNullOrEmpty()) {
    debugFile = debugFile.replace(".json", suffix + ".json")
  }
  return createDebugLoggerWithFile(debugFile)
}

fun createDebugLoggerWithFile(debugFile: String): MessagingLogger? {
  val queue = ConcurrentLinkedQueue<LogEntry>()
  val logger = MessagingLogger(queue)
  ApplicationManager.getApplication().executeOnPooledThread {
    val file = File(FileUtil.expandUserHome(debugFile))
    FileUtilRt.createParentDirs(file)
    val out = FileOutputStream(file)
    val writer = out.writer()
    writer.write("[\n")
    writer.flush()
    val fileChannel = out.channel

    val dateFormatter = SimpleDateFormat("HH.mm.ss,SSS")

    while (true) {
      val entry = queue.poll() ?: if (logger.closed) {
        break
      }
      else {
        continue
      }

      writer.write("""{"timestamp": "${dateFormatter.format(entry.time)}", """)
      val message = entry.message
      when (message) {
        is CharSequence -> {
          writer.write("\"${entry.marker}\": ")
          writer.flush()

          if (message is CharSequenceBackedByChars) {
            fileChannel.write(message.byteBuffer)
          }
          else {
            fileChannel.write(Charsets.UTF_8.encode(CharBuffer.wrap(message)))
          }

          writer.write("},\n")
          writer.flush()
        }
        is ByteBuf -> {
          writer.write("\"${entry.marker}\": ")
          writer.flush()

          message.getBytes(message.readerIndex(), out, message.readableBytes())
          message.release()

          writer.write("},\n")
          writer.flush()
        }
        else -> throw RuntimeException("Unknown message type")
      }
    }
    writer.write("]")
    writer.flush()
    out.close()
  }
  return logger
}
