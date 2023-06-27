package com.intellij.remoteDev.util

import com.intellij.util.io.Compressor
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.zip.ZipInputStream

@ApiStatus.Internal
fun Compressor.addAllFromZip(zipPath: Path, prefix: String = "") {
  ZipInputStream(zipPath.toFile().inputStream()).use { zipStream ->
    var entry = zipStream.nextEntry
    while (entry != null) {
      val path = entry.name.trim('/', '\\')
      if (path.contains("..")) {
        continue
      }

      val newEntryName = Path.of(prefix, path).toString().replace('\\', '/')
      val entryTime = entry.lastModifiedTime.toMillis()
      if (entry.isDirectory)
        addDirectory(newEntryName, entryTime)
      else
        addFile(newEntryName, zipStream, entryTime)

      entry = zipStream.nextEntry
    }
  }
}