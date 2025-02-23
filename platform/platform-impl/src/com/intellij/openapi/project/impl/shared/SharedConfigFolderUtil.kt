// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl.shared

import com.intellij.openapi.application.Application
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.TimeoutUtil
import com.intellij.util.application
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.messages.Topic
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel
import java.nio.channels.FileChannel
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.random.Random

interface ConfigFolderChangedListener {
  companion object {
    @JvmField
    val TOPIC: Topic<ConfigFolderChangedListener> = Topic(ConfigFolderChangedListener::class.java.simpleName, ConfigFolderChangedListener::class.java)
  }

  fun onChange(changedFileSpecs: Set<String>, deletedFileSpecs: Set<String>)
}

object SharedConfigFolderUtil {
  private val LOG = logger<SharedConfigFolderUtil>()

  fun installStreamProvider(application: Application, path: Path) {
    val storageManager = application.stateStore.storageManager
    storageManager.addStreamProvider(SharedConfigFolderStreamProvider(path), first = true)
  }

  /**
   * @param path $ROOT_CONFIG$ to watch (aka <config>, idea.config.path)
   */
  fun installFsWatcher(path: Path) {
    SharedConfigFolderNioListener(path).init()
  }

  internal fun reloadComponents(changedFileSpecs: Set<String>, deletedFileSpecs: Set<String>) {
    application.messageBus.syncPublisher(ConfigFolderChangedListener.TOPIC).onChange(changedFileSpecs, deletedFileSpecs)
  }

  fun writeToSharedFile(file: Path, content: ByteArray) {
    val writeOptions = arrayOf(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    file.parent.createDirectories()
    writeWithRetries(file, *writeOptions) { out ->
      out.writeAll(ByteBuffer.wrap(content))
    }
  }
  
  fun deleteSharedFile(file: Path) {
    ioWithRetries {
      if (file.exists()) {
        file.delete()
      }
    }
  }

  /**
   * Tries to read content of the file which is assumed to be non-empty. 
   * If the file is actually empty, it probably means that it's being written to at the moment, so retry reading after some delay.
   */
  internal fun <V> readNonEmptyFileWithRetries(path: Path, handle: (InputStream) -> V): V {
    return ioWithRetries({
      if (path.fileSize() > 0L) {
        path.inputStream()
      }
      else {
        throw IOException("empty file")
      }
    }, handle)
  }
  
  private fun <T, V> ioWithRetries(open: () -> T, handle: (T) -> V): V {
    val maxAttempts = 5
    var attempts = 0
    var openSuccess = false
    while (true) {
      try {
        attempts++

        val value = open()
        openSuccess = true

        return handle(value)
      }
      catch (e: IOException) {
        if (openSuccess || attempts > maxAttempts) {
          throw e
        }
        else {
          LOG.debug { "retrying reading after $e, attempt $attempts" }
          TimeoutUtil.sleep(attempts * 50L + Random.nextInt(50)) // let another IDE release file lock
        }
      }
    }
  }

  private fun <T> ioWithRetries(open: () -> T): T = ioWithRetries(open) { it }

  private fun <T> writeWithRetries(file: Path, vararg options: OpenOption, task: (FileChannel) -> T): T {
    return ioWithRetries(
      {
        val channel = FileChannel.open(file, *options)
        channel.lock() to channel
      },
      { (lock, channel) ->
        channel.use {
          lock.use { task(channel) }
        }
      }
    )
  }

  private fun ByteChannel.writeAll(buf: ByteBuffer): Boolean {
    while (buf.hasRemaining()) {
      if (write(buf) < 0) return false
    }
    return true
  }
}