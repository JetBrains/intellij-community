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
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.CharSequenceBackedByChars
import com.intellij.util.io.addChannelListener
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import org.jetbrains.annotations.PropertyKey
import java.io.File
import java.io.FileOutputStream
import java.nio.CharBuffer
import java.text.SimpleDateFormat
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal class LogEntry(val message: CharSequence, val marker: String) {
  internal val time = System.currentTimeMillis()
}

class MessagingLogger internal constructor(debugFile: String) {
  private val processFuture: Future<*>
  private val queue = LinkedBlockingQueue<LogEntry>()

  init {
    processFuture = ApplicationManager.getApplication().executeOnPooledThread {
      val file = File(FileUtil.expandUserHome(debugFile))
      FileUtilRt.createParentDirs(file)
      val out = FileOutputStream(file)
      val writer = out.writer()
      writer.write("[\n")
      writer.flush()
      val fileChannel = out.channel

      val dateFormatter = SimpleDateFormat("HH.mm.ss,SSS")

      try {
        while (true) {
          val entry = queue.take()

          writer.write("""{"timestamp": "${dateFormatter.format(entry.time)}", """)
          val message = entry.message
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
      }
      catch (e: InterruptedException) {
      }
      finally {
        writer.write("]")
        writer.flush()
        out.close()
      }
    }
  }

  fun add(message: CharSequence, marker: String = "IN") {
    // ignore Network events
    if (!message.startsWith("{\"method\":\"Network.")) {
      queue.add(LogEntry(message, marker))
    }
  }

  fun add(outMessage: ByteBuf, marker: String = "OUT") {
    val charSequence = outMessage.getCharSequence(outMessage.readerIndex(), outMessage.readableBytes(), Charsets.UTF_8)
    add(charSequence, marker)
  }

  fun close() {
    AppExecutorUtil.getAppScheduledExecutorService().schedule(fun() {
      processFuture.cancel(true)
    }, 1, TimeUnit.SECONDS)
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
  if (debugFile.isEmpty()) {
    return null
  }

  if (!suffix.isEmpty()) {
    debugFile = debugFile.replace(".json", suffix + ".json")
  }
  return MessagingLogger(debugFile)
}
