// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LocalFileSystemEelUtil")

package com.intellij.openapi.vfs.impl.local

import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.limits.FileSizeLimit
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.getOrThrowFileSystemException
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

private val map = ContainerUtil.createConcurrentWeakMap<Path, EelApi>();

/**
 * [java.nio.file.Files.readAllBytes] takes five separate syscalls to complete.
 * This is unacceptable in the remote setting when each request to IO results in RPC.
 * Here we try to invoke a specialized function that can read all bytes from [path] in one request.
 */
@Suppress("RAW_RUN_BLOCKING")
internal fun readWholeFileIfNotTooLargeWithEel(path: Path): ByteArray? {
  if (!Registry.`is`("vfs.try.eel.for.content.loading", false)) {
    return null
  }
  val root = path.root ?: return null
  val eelDescriptor = root.getEelDescriptor()
  if (eelDescriptor == LocalEelDescriptor) {
    return null
  }
  val api = map.computeIfAbsent(root) {
    runBlocking {
      root.getEelDescriptor().upgrade()
    }
  }
  if (api is LocalEelApi) {
    return null
  }
  val eelPath = path.asEelPath()
  val limit = FileSizeLimit.getContentLoadLimit(FileUtilRt.getExtension(path.fileName.toString()))

  return runBlocking {
    when (val res = api.fs.readFully(eelPath, limit.toULong(), EelFileSystemApi.OverflowPolicy.DROP).getOrThrowFileSystemException()) {
      is EelFileSystemApi.FullReadResult.Bytes -> res.bytes
      is EelFileSystemApi.FullReadResult.BytesOverflown -> error("Never returned")
      is EelFileSystemApi.FullReadResult.Overflow -> throw FileTooBigException("File $path is bigger than $limit bytes")
    }
  }
}