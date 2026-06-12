// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local.windows

import org.jetbrains.annotations.ApiStatus
import java.io.Closeable
import java.io.IOException
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.nio.file.DirectoryIteratorException
import java.nio.file.DirectoryStream
import java.nio.file.NotDirectoryException
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
class WindowsBufferedDirectoryStream @Throws(IOException::class) constructor(val directory: Path) : DirectoryStream<com.intellij.openapi.util.Pair<Path, BasicFileAttributes>> {

  val iteraror: WindowsBufferedDirectoryIterator = WindowsBufferedDirectoryIterator(directory)
  var isCalled: Boolean = false

  override fun iterator(): MutableIterator<com.intellij.openapi.util.Pair<Path, BasicFileAttributes>> {
    if (!isCalled) {
      isCalled = true
      return iteraror
    }
    throw IllegalStateException()
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

  override fun lastModifiedTime(): FileTime {
    return lastWriteTime
  }

  override fun lastAccessTime(): FileTime {
    return lastAccessTime
  }

  override fun creationTime(): FileTime {
    return creationTime
  }

  override fun isRegularFile(): Boolean {
    return !isSymbolicLink && !isDirectory && !isOther
  }

  override fun isDirectory(): Boolean {
    return !isSymbolicLink && (fileAttributes and Windows.FileAttributes.Directory) != 0
  }

  @OptIn(ExperimentalAtomicApi::class)
  private val isSymbolicLinkCached: AtomicReference<Boolean?> = AtomicReference(null)
  // The responsibility to cache and invalidate should lie with the caller (?)
  @OptIn(ExperimentalAtomicApi::class)
  override fun isSymbolicLink(): Boolean {
    if ((fileAttributes and Windows.FileAttributes.ReparsePoint) == 0) return false
    val cached = isSymbolicLinkCached.load()
    if (cached != null) return cached

    Arena.ofConfined().use { arena ->
      val api = Windows(arena)

      val handle = api.CreateFileW(
        adaptForLongPathHandling(parentPath.toAbsolutePath().pathString), // not allowed
        dwDesiredAccess = Windows.FileOperations.FILE_GENERIC_READ,
        dwShareMode = Windows.FileShare.FILE_SHARE_READWRITE,
        lpSecurityAttributes = Windows.NULL,
        dwCreationDisposition = Windows.FileMode.OPEN_EXISTING,
        dwFlagsAndAttributes = Windows.FileOperations.FILE_OPEN_REPARSE_POINT or Windows.FileOperations.FILE_FLAG_BACKUP_SEMANTICS,
        hTemplateFile = Windows.NULL
      )

      val handleValue = Windows.Read.HANDLE(handle)
      if (handleValue == 0L || handleValue == -1L) {
        throw Windows.Error.win32ErrorToIOException(api.GetLastFFIError(), parentPath)
      }

      try {
        val reparsePointDataBuffer: MemorySegment = arena.allocate(MAX_REPARSE_POINT_DATA_SIZE)

        val returnCode = api.DeviceIoControl(
          handle,
          Windows.DeviceIoControlCodes.FSCTL_GET_REPARSE_POINT.toInt(),
          Windows.NULL,
          0,
          reparsePointDataBuffer,
          MAX_REPARSE_POINT_DATA_SIZE.toInt(),
          Windows.NULL,
          Windows.NULL
        )

        if (returnCode == 0) {
          throw Windows.Error.win32ErrorToIOException(api.GetLastFFIError(), parentPath)
        }

        val isSymbolicLink = Windows.Read.REPARSE_DATA_BUFFER.Tag.IO_REPARSE_TAG_SYMLINK == Windows.Read.REPARSE_DATA_BUFFER.ReparseTag(reparsePointDataBuffer)
        isSymbolicLinkCached.store(isSymbolicLink)
        return isSymbolicLink
      }
      finally {
        api.CloseHandle(handle)
      }
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

// That's insane but that's WinAPI
// All dates and times are in absolute system-time format. Absolute system time is the number of 100-nanosecond intervals since the start of the year 1601.
private fun convertFileTimeToMs(time: Long): Long {
  return time / 10_000 - 11_644_473_600_000L
}

/**
 * Arena.ofShared closing issues a GLOBAL HANDSHAKE, which is roughly equivalent to a global safepoint, but isn't tracked by the JFR
 * and resumes each thread after it gets its HANDSHAKE CALLBACK done, not after ALL threads are done.
 *
 * SO ITS LIKE CRAZY SLOW
 *
 * So instances of this class will have to be confined to a single thread if we should use project panama to access the Windows API from it
 */
@ApiStatus.Internal
class WindowsBufferedDirectoryIterator(val directory: Path) : MutableIterator<com.intellij.openapi.util.Pair<Path, BasicFileAttributes>>, Closeable {
  internal val api: Windows = Windows(Arena.ofConfined())

  var directoryHandle: MemorySegment = Windows.NULL

  private var lastEntryFound = false
  var currentEntry: MemorySegment = Windows.NULL

  lateinit var currentPath: Path
  lateinit var buffer: MemorySegment

  init {
    val fullCanonicalPath = directory.toAbsolutePath().pathString
    try {
      directoryHandle = createDirectoryHandle(adaptForLongPathHandling(fullCanonicalPath))
    }
    catch (e: Exception) {
      api.close()
      throw e
    }
    currentPath = directory
  }

  fun createDirectoryHandle(path: String): MemorySegment {
    // CreateFileW with FILE_FLAG_BACKUP_SEMANTICS opens regular files too, so reject non-directories up front.
    val attributes = api.GetFileAttributesW(path)
    if (attributes == Windows.FileAttributes.INVALID_FILE_ATTRIBUTES) {
      throw Windows.Error.win32ErrorToIOException(api.GetLastFFIError(), directory)
    }
    if ((attributes and Windows.FileAttributes.Directory) == 0) {
      throw NotDirectoryException(directory.toString())
    }

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
      throw Windows.Error.win32ErrorToIOException(api.GetLastFFIError(), directory)
    }

    buffer = Windows.Alloc.ByteBuffer(api.arena, STANDARD_BUFFER_SIZE)

    return handle
  }

  fun closeCurrentDirectoryHandleIfOpen() {
    val handle = directoryHandle
    directoryHandle = Windows.NULL
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
    val handle = directoryHandle
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
          directoryFinished()
          throw DirectoryIteratorException(Windows.Error.win32ErrorToIOException(error, directory))
        }
      }
    }
  }

  private fun directoryFinished() {
    currentEntry = Windows.NULL
    lastEntryFound = true
    closeCurrentDirectoryHandleIfOpen()
  }

  override fun remove() {
    // noop
  }

  override fun next(): com.intellij.openapi.util.Pair<Path, BasicFileAttributes> {
    val it = nextPairChecked
    nextPairChecked = null
    if (it != null) return it

    if (hasNext()) {
      val it = nextPairChecked
      nextPairChecked = null
      if (it != null) return it
    }
    throw NoSuchElementException()
  }

  private var nextPairChecked: com.intellij.openapi.util.Pair<Path, BasicFileAttributes>? = null

  override fun hasNext(): Boolean {
    if (nextPairChecked != null) {
      return true
    }

    if (lastEntryFound)
      return false

    while (true) {
      findNextEntry()

      if (lastEntryFound)
        return false

      val len = Windows.Read.FILE_FULL_DIR_INFORMATION.FileNameLength(currentEntry)
      val fileName = Windows.Read.FILE_FULL_DIR_INFORMATION.fetchFileNameSegment(len, currentEntry, buffer)
      val javaFileName = toJavaStringFromWinCWSTR(fileName, len / 2)

      if (javaFileName.length == 1 && javaFileName[0] == '.' || javaFileName.length == 2 && javaFileName[0] == '.' && javaFileName[1] == '.') {
        continue
      }

      val path = directory / javaFileName
      val attrs = NTWindowsFileAttributes(
        FileTime.from(convertFileTimeToMs(Windows.Read.FILE_FULL_DIR_INFORMATION.CreationTime(currentEntry)), TimeUnit.MILLISECONDS),
        FileTime.from(convertFileTimeToMs(Windows.Read.FILE_FULL_DIR_INFORMATION.LastAccessTime(currentEntry)), TimeUnit.MILLISECONDS),
        FileTime.from(convertFileTimeToMs(Windows.Read.FILE_FULL_DIR_INFORMATION.LastWriteTime(currentEntry)), TimeUnit.MILLISECONDS),
        Windows.Read.FILE_FULL_DIR_INFORMATION.FileAttributes(currentEntry),
        Windows.Read.FILE_FULL_DIR_INFORMATION.EndOfFile(currentEntry),
        path
      )

      nextPairChecked = com.intellij.openapi.util.Pair(path, attrs)

      return true
    }
  }

  override fun close() {
    closeCurrentDirectoryHandleIfOpen()
    api.close()
  }
}
