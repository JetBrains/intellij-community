// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.createSymbolicLink
import com.intellij.util.io.outputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

fun unpackTarGz(inputStream: InputStream, to: Path) {
  TarArchiveInputStream(GzipCompressorInputStream(inputStream)).use { input ->
    while (true) {
      val entry = input.nextTarEntry ?: break
      if (entry.isDirectory) {
        continue
      }

      val name = entry.name
      val destination = to.resolve(name)
      // can be empty string
      if (entry.linkName.isNullOrBlank()) {
        destination.outputStream().use { FileUtil.copy(input, entry.size, it) }
        if (SystemInfo.isUnix && entry.mode and 0b001001001 != 0) {
          Files.setPosixFilePermissions(destination, setOf(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ,
                                                           PosixFilePermission.OWNER_WRITE))
        }
      }
      else {
        // link relative to "from" path
        if (SystemInfo.isWindows) {
          Logger.getInstance("#org.jetbrains.io").warn(
            "Skipped ${entry.linkName} in ${destination}. Creating symbolic links on Windows requires admin permissions")
        }
        else {
          destination.createSymbolicLink(destination.parent.resolve(entry.linkName))
        }
      }
    }
  }
}
