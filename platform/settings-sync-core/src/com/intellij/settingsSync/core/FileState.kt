package com.intellij.settingsSync.core

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemIndependent
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo

@ApiStatus.Internal
sealed class FileState(open val file: @SystemIndependent String) {

  class Modified(override val file: @SystemIndependent String, val content: ByteArray) : FileState(file) {
    override fun toString(): String = "file='$file', content:\n${String(content, StandardCharsets.UTF_8)}"

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Modified

      if (file != other.file) return false
      if (!content.contentEquals(other.content)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = file.hashCode()
      result = 31 * result + content.contentHashCode()
      return result
    }
  }

  data class Deleted(override val file: @SystemIndependent String): FileState(file)
}

internal fun getFileStateFromFileWithDeletedMarker(file: Path, storageBasePath: Path): FileState {
  val bytes = file.readBytes()
  val text = String(bytes, Charset.defaultCharset())
  val fileSpec = file.relativeTo(storageBasePath).invariantSeparatorsPathString
  return if (text == DELETED_FILE_MARKER) {
    FileState.Deleted(fileSpec)
  }
  else {
    FileState.Modified(fileSpec, bytes)
  }
}

internal fun collectFileStatesFromFiles(paths: Set<Path>, rootConfigPath: Path): Set<FileState> {
  val fileStates = mutableSetOf<FileState>()
  for (path in paths) {
    if (path.isRegularFile()) {
      fileStates += getFileStateFromFileWithDeletedMarker(path, rootConfigPath)
    }
    else { // 'path' is e.g. 'ROOT_CONFIG/keymaps/'
      val fileStatesFromFolder = path.toFile().walkTopDown()
        .filter { it.isFile }
        .mapTo(HashSet()) { getFileStateFromFileWithDeletedMarker(it.toPath(), rootConfigPath) }
      fileStates.addAll(fileStatesFromFolder)
    }
  }
  return fileStates
}

internal const val DELETED_FILE_MARKER = "DELETED"
