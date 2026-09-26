// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.ApiStatus
import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.MemorySegment
import java.lang.foreign.StructLayout
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_SHORT
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * The reparse point of a path. Windows only.
 *
 * NTFS keeps a reparse point on a file or on a directory. The point holds a tag and a payload. The tag names the
 * component that owns the point, and it gives the layout of the payload. Windows sends a request for such a path
 * to that component, and the component redirects the request to the real target.
 *
 * See [read].
 *
 * @property tag the reparse tag, for example [IO_REPARSE_TAG_SYMLINK]
 * @property data the payload that follows the header. The tag gives the layout of these bytes.
 */
@ApiStatus.Internal
class WindowsReparsePoint private constructor(
  val tag: Int,
  val data: ByteArray,
) {
  override fun toString(): String = "WindowsReparsePoint(tag=0x${Integer.toHexString(tag)}, data=${data.size} bytes)"

  companion object {
    /** `IO_REPARSE_TAG_MOUNT_POINT`: a junction, or a volume mount point. */
    const val IO_REPARSE_TAG_MOUNT_POINT: Int = 0xA0000003.toInt()

    /** `IO_REPARSE_TAG_SYMLINK`: a symbolic link. */
    const val IO_REPARSE_TAG_SYMLINK: Int = 0xA000000C.toInt()

    /** `IO_REPARSE_TAG_APPEXECLINK`: a link to an AppX application. */
    const val IO_REPARSE_TAG_APPEXECLINK: Int = 0x8000001B.toInt()

    /** `ERROR_NOT_A_REPARSE_POINT`: the code that the read gives for a path without a reparse point. */
    const val ERROR_NOT_A_REPARSE_POINT: Int = 4390

    /**
     * Reads the reparse point of [path] with `DeviceIoControl(FSCTL_GET_REPARSE_POINT)`.
     *
     * The open uses `FILE_FLAG_OPEN_REPARSE_POINT`, so it opens the point itself. Without that flag Windows
     * follows the point to its target. The open also uses `FILE_FLAG_BACKUP_SEMANTICS`, because a directory
     * does not open without it.
     *
     * @param path a file or a directory that carries a reparse point
     * @return the tag and the payload, or a failure that names the call and its error code. A path that carries
     * no reparse point gives a [WindowsException] with the code [ERROR_NOT_A_REPARSE_POINT].
     */
    @ApiStatus.Internal
    fun read(path: Path): Result<WindowsReparsePoint> {
      assert(SystemInfoRt.isWindows) { "Do not call on **nix" }

      Arena.ofConfined().use { arena ->
        val callState = arena.allocate(WindowsKernel32.CALL_STATE)
        val name = arena.allocateFrom(windowsPathString(path), StandardCharsets.UTF_16LE)
        val file = WindowsKernel32.createFile(
          callState,
          name,
          WindowsKernel32.FILE_READ_ATTRIBUTES,
          WindowsKernel32.FILE_SHARE_ALL,
          WindowsKernel32.OPEN_EXISTING,
          WindowsKernel32.FILE_FLAG_OPEN_REPARSE_POINT or WindowsKernel32.FILE_FLAG_BACKUP_SEMANTICS,
        )
        if (file.address() == WindowsKernel32.INVALID_HANDLE_VALUE) {
          return winFailure("CreateFileW failed for $path", WindowsKernel32.lastError(callState))
        }

        try {
          val buffer = arena.allocate(REPARSE_DATA_BUFFER)
          val returned = arena.allocate(JAVA_INT)
          val succeeded = WindowsKernel32.deviceIoControl(
            callState, file, FSCTL_GET_REPARSE_POINT, MemorySegment.NULL, 0, buffer, buffer.byteSize().toInt(), returned)
          if (!succeeded) {
            return winFailure("FSCTL_GET_REPARSE_POINT failed for $path", WindowsKernel32.lastError(callState))
          }

          val written = Integer.toUnsignedLong(returned.get(JAVA_INT, 0))
          if (written < HEADER_SIZE) {
            return winFailure("FSCTL_GET_REPARSE_POINT wrote $written bytes for $path", 0)
          }

          // ReparseDataLength is a USHORT, so read it unsigned. It counts the payload and excludes the header.
          // The kernel owns the value, so trust no more bytes than the call reports.
          val reported = buffer.get(JAVA_SHORT, REPARSE_DATA_LENGTH).toUShort().toLong()
          val length = minOf(reported, written - HEADER_SIZE)

          val tag = buffer.get(JAVA_INT, REPARSE_TAG)
          val data = buffer.asSlice(DATA_BUFFER, length).toArray(JAVA_BYTE)
          return Result.success(WindowsReparsePoint(tag, data))
        }
        finally {
          WindowsKernel32.closeHandle(file)
        }
      }
    }
  }
}

/** `MAXIMUM_REPARSE_DATA_BUFFER_SIZE` from `ntifs.h`. The file system never answers more bytes. */
private const val MAXIMUM_REPARSE_DATA_BUFFER_SIZE = 16L * 1024

/** `REPARSE_DATA_BUFFER_HEADER_SIZE`: the tag, the data length and the reserved field take 8 bytes. */
private const val HEADER_SIZE = 8L

/** `FSCTL_GET_REPARSE_POINT` = `CTL_CODE(FILE_DEVICE_FILE_SYSTEM, 42, METHOD_BUFFERED, FILE_ANY_ACCESS)`. */
private const val FSCTL_GET_REPARSE_POINT = 0x000900A8

/**
 * `REPARSE_DATA_BUFFER` from `ntifs.h`. The header takes 8 bytes, and the payload takes the rest.
 *
 * The payload member holds the largest answer that the file system gives, so the size of the layout is also the
 * size of the output buffer.
 */
private val REPARSE_DATA_BUFFER: StructLayout = MemoryLayout.structLayout(
  JAVA_INT.withName("ReparseTag"),
  JAVA_SHORT.withName("ReparseDataLength"),
  JAVA_SHORT.withName("Reserved"),
  MemoryLayout.sequenceLayout(MAXIMUM_REPARSE_DATA_BUFFER_SIZE - HEADER_SIZE, JAVA_BYTE).withName("DataBuffer"))

private val REPARSE_TAG: Long = REPARSE_DATA_BUFFER.byteOffset(groupElement("ReparseTag"))
private val REPARSE_DATA_LENGTH: Long = REPARSE_DATA_BUFFER.byteOffset(groupElement("ReparseDataLength"))
private val DATA_BUFFER: Long = REPARSE_DATA_BUFFER.byteOffset(groupElement("DataBuffer"))
