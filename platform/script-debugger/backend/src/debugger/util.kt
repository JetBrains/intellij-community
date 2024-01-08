// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.text.CharSequenceBackedByChars
import com.intellij.util.io.addChannelListener
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import java.io.File
import java.io.FileOutputStream
import java.nio.CharBuffer
import java.text.SimpleDateFormat
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class LogEntry(val message: CharSequence, val marker: String) {
  internal val time = System.currentTimeMillis()
}

class MessagingLogger internal constructor(debugFileBaseName: String, suffix: String) {
  private val processFuture: Future<*>
  private val queue = LinkedBlockingQueue<LogEntry>()

  init {
    processFuture = ApplicationManager.getApplication().executeOnPooledThread {
      val sessionNumber = sequentialNumber.getAndIncrement()
      val nameSuffix = if (sessionNumber == 0) "$suffix.json" else "$suffix-$sessionNumber.json"

      val file = File(FileUtil.expandUserHome(debugFileBaseName + nameSuffix))
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
        // ignored
      }
      finally {
        writer.write("]")
        writer.flush()
        sequentialNumber.decrementAndGet()
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

  companion object {
    private val sequentialNumber = AtomicInteger(0)
  }
}

fun createDebugLogger(key: String, suffix: String = ""): MessagingLogger? {
  var debugFile = Registry.stringValue(key)
  if (debugFile.isEmpty()) {
    return null
  }

  debugFile = debugFile.replace(".json", "")
  return MessagingLogger(debugFile, suffix)
}
