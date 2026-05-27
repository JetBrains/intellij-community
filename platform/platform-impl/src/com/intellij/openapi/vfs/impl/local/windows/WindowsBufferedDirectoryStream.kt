// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local.windows

import org.jetbrains.annotations.ApiStatus
import java.io.Closeable
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.nio.file.DirectoryStream
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.div
import kotlin.io.path.pathString

private const val STANDARD_BUFFER_SIZE = 4096L
private const val MAX_REPARSE_POINT_DATA_SIZE = 16384L

@ApiStatus.Internal
class WindowsBufferedDirectoryStream(val directory: Path) : DirectoryStream<com.intellij.openapi.util.Pair<Path, BasicFileAttributes>> {

  val iteraror = WindowsBufferedDirectoryIterator(directory)

  override fun iterator(): MutableIterator<com.intellij.openapi.util.Pair<Path, BasicFileAttributes>> {
    return iteraror
  }

  override fun close() {
    iteraror.close()
  }

}

internal class NTWindowsFileAttributes(
  private val creationTime: FileTime,
  private val lastAccessTime: FileTime,
  private val lastWriteTime: FileTime,
  private val fileAttributes: Int,
  private val size: Long,
  private val parentPath: Path,
) : DosFileAttributes {

  override fun lastModifiedTime(): FileTime? {
    return lastWriteTime
  }

  override fun lastAccessTime(): FileTime? {
    return lastAccessTime
  }

  override fun creationTime(): FileTime? {
    return creationTime
  }

  override fun isRegularFile(): Boolean {
    return !isSymbolicLink && !isDirectory && !isOther
  }

  override fun isDirectory(): Boolean {
    if (isSymbolicLink) return false

    return (fileAttributes and Windows.FileAttributes.Directory) != 0
  }

  // The responsibility to cache and invalidate should lie with the caller (?)
  override fun isSymbolicLink(): Boolean {
    if ((fileAttributes and Windows.FileAttributes.ReparsePoint) == 0) return false
    Arena.ofConfined().use { arena ->
      val api = Windows(arena)

      val handle = api.CreateFileW(
        parentPath.toAbsolutePath().pathString, // not allowed
        dwDesiredAccess = Windows.FileOperations.FILE_GENERIC_READ,
        dwShareMode = Windows.FileShare.FILE_SHARE_READWRITE,
        lpSecurityAttributes = Windows.NULL,
        dwCreationDisposition = Windows.FileMode.OPEN_EXISTING,
        dwFlagsAndAttributes = Windows.FileOperations.FILE_OPEN_REPARSE_POINT,
        hTemplateFile = Windows.NULL
      )

      val reparsePointDataBuffer: MemorySegment = arena.allocate(MAX_REPARSE_POINT_DATA_SIZE)

      api.DeviceIoControl(
        handle,
        Windows.DeviceIoControlCodes.FSCTL_GET_REPARSE_POINT.toInt(),
        Windows.NULL,
        0,
        reparsePointDataBuffer,
        MAX_REPARSE_POINT_DATA_SIZE.toInt(),
        Windows.NULL,
        Windows.NULL
      )

      api.CloseHandle(handle)

      return Windows.Read.REPARSE_DATA_BUFFER.Tag.IO_REPARSE_TAG_SYMLINK == Windows.Read.REPARSE_DATA_BUFFER.ReparseTag(reparsePointDataBuffer)
    }
  }

  override fun isOther(): Boolean {
    return !isSymbolicLink && ((fileAttributes and (Windows.FileAttributes.Device or Windows.FileAttributes.ReparsePoint)) != 0)
  }

  override fun size(): Long {
    return size
  }

  override fun fileKey(): Any? {
    return null
  }

  override fun isReadOnly(): Boolean {
    return (fileAttributes and Windows.FileAttributes.ReadOnly) != 0
  }

  override fun isHidden(): Boolean {
    return (fileAttributes and Windows.FileAttributes.Hidden) != 0
  }

  override fun isArchive(): Boolean {
    return (fileAttributes and Windows.FileAttributes.Archive) != 0
  }

  override fun isSystem(): Boolean {
    return (fileAttributes and Windows.FileAttributes.System) != 0
  }

}

private const val MAX_PATH = 260

private fun adaptForLongPathHandling(path: String): String {
    if (path.length < MAX_PATH) return path
    return "\\\\?\\$path"
}

@OptIn(ExperimentalAtomicApi::class)
@ApiStatus.Internal
class WindowsBufferedDirectoryIterator(val directory: Path) : MutableIterator<com.intellij.openapi.util.Pair<Path, BasicFileAttributes>>, Closeable {
  internal val api: Windows = Windows(Arena.ofShared())

  val directoryHandle: AtomicReference<MemorySegment> = AtomicReference(Windows.NULL)

  private var lastEntryFound = false
  var currentEntry: MemorySegment = Windows.NULL

  lateinit var currentPath: Path
  lateinit var buffer: MemorySegment

  init {
    val fullCanonicalPath = directory.toAbsolutePath().pathString
    directoryHandle.store(createDirectoryHandle(adaptForLongPathHandling(fullCanonicalPath)))
    currentPath = directory
  }

  fun createDirectoryHandle(path: String): MemorySegment {
    val handle = api.CreateFileW(
      path,
      dwDesiredAccess = Windows.FileOperations.FILE_LIST_DIRECTORY,
      dwShareMode = Windows.FileShare.FILE_SHARE_READWRITE or Windows.FileShare.FILE_SHARE_DELETE,
      lpSecurityAttributes = Windows.NULL,
      dwCreationDisposition = Windows.FileMode.OPEN_EXISTING,
      dwFlagsAndAttributes = Windows.FileOperations.FILE_FLAG_BACKUP_SEMANTICS,
      hTemplateFile = Windows.NULL
    )
    val handleValue = Windows.Read.HANDLE(handle)

    if (handleValue == 0L || handleValue == -1L) {
      val winError = api.GetLastFFIError()
      // TODO handle via Winapi's FormatMessage
      throw Exception("Windows error code: $winError, path: $path")
    }

    buffer = Windows.Alloc.ByteBuffer(api.arena, STANDARD_BUFFER_SIZE)

    return handle
  }

  fun closeCurrentDirectoryHandle() {
    val handle = directoryHandle.exchange(Windows.NULL)
    if (handle != Windows.NULL) {
      api.CloseHandle(handle)
    }
  }

  private fun findNextEntry() {
    currentEntry = Windows.Read.FILE_FULL_DIR_INFORMATION.fetchNextInBuffer(currentEntry, buffer)
    if (currentEntry != Windows.NULL) return

    if (getData()) {
      currentEntry = Windows.Read.FILE_FULL_DIR_INFORMATION.fetchFirstInBuffer(buffer)
    }
  }

  private fun getData(): Boolean {
    val handle = directoryHandle.load()
    assert(handle != Windows.NULL && Windows.Read.HANDLE(handle) != -1L && !lastEntryFound)
    Arena.ofConfined().use { arena ->
      val ioStatusBlock = Windows.Alloc.IO_STATUS_BLOCK(arena)
      val status = api.NtQueryDirectoryFile(
        fileHandle = handle,
        event = Windows.NULL,
        apcRoutine = Windows.NULL,
        apcContext = Windows.NULL,
        ioStatusBlock = ioStatusBlock,
        fileInformation = buffer,
        length = STANDARD_BUFFER_SIZE.toInt(),
        fileInformationClass = Windows.FILE_FULL_DIR_INFORMATION.FILE_FULL_DIRECTORY_INFORMATION,
        returnSingleEntry = 0,
        fileName = Windows.NULL,
        restartScan = 0
      )

      when (status) {
        Windows.NtStatus.STATUS_NO_MORE_FILES -> {
          directoryFinished()
          return false
        }
        Windows.NtStatus.STATUS_SUCCESS -> {
          assert(Windows.Read.IO_STATUS_BLOCK.Information(ioStatusBlock) != 0L)
          return true
        }
        Windows.NtStatus.STATUS_FILE_NOT_FOUND -> {
          directoryFinished()
          return false
        }
        else -> {
          val error = api.RtlNtStatusToDosError(status)
          throw Exception("Windows error code: $error")
        }
      }
    }
  }

  private fun directoryFinished() {
    currentEntry = Windows.NULL
    lastEntryFound = true
    closeCurrentDirectoryHandle()
  }

  override fun remove() {
    // noop
  }

  override fun next(): com.intellij.openapi.util.Pair<Path, BasicFileAttributes> {
    return nextPairChecked ?: throw NoSuchElementException()
  }

  private var nextPairChecked: com.intellij.openapi.util.Pair<Path, BasicFileAttributes>? = null

  override fun hasNext(): Boolean {
    if (lastEntryFound)
      return false

    synchronized(this) {
      if (lastEntryFound)
        return false

      while (true) {
        findNextEntry()

        if (lastEntryFound)
          return false

        val len = Windows.Read.FILE_FULL_DIR_INFORMATION.FileNameLength(currentEntry)
        val fileName = Windows.Read.FILE_FULL_DIR_INFORMATION.fetchFileNameSegment(len, currentEntry, buffer)
        val javaFileName = toJavaStringFromWinCWSTR(fileName, len.toInt() / 2)

        if (javaFileName.length == 1 && javaFileName[0] == '.' || javaFileName.length == 2 && javaFileName[0] == '.' && javaFileName[1] == '.') {
          continue
        }

        val path = directory / javaFileName
        val attrs = NTWindowsFileAttributes(
          FileTime.from(Windows.Read.FILE_FULL_DIR_INFORMATION.CreationTime(currentEntry), TimeUnit.MILLISECONDS),
          FileTime.from(Windows.Read.FILE_FULL_DIR_INFORMATION.LastAccessTime(currentEntry), TimeUnit.MILLISECONDS),
          FileTime.from(Windows.Read.FILE_FULL_DIR_INFORMATION.LastWriteTime(currentEntry), TimeUnit.MILLISECONDS),
          Windows.Read.FILE_FULL_DIR_INFORMATION.FileAttributes(currentEntry),
          Windows.Read.FILE_FULL_DIR_INFORMATION.EndOfFile(currentEntry),
          path,
        )

        nextPairChecked = com.intellij.openapi.util.Pair(path, attrs)

        return true
      }
    }
  }

  override fun close() {
    api.close()
  }
}
