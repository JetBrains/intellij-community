// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.ApiStatus
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.jvm.optionals.getOrNull

/**
 * The processes that hold a path open. Windows only.
 */
@ApiStatus.Internal
object WindowsFileLocks {
  /** A path is rarely open in many processes, so the first try usually needs no second call. */
  private const val FIRST_CAPACITY = 16

  /** A guard against an endless loop. 4096 process ids need 32 KB. */
  private const val MAX_CAPACITY = 4096

  /**
   * Asks the kernel which processes hold [path] open. A locked path does not delete, so the answer names the
   * processes that block the delete.
   *
   * @param path a file or a directory
   * @return the processes that hold [path] open, or a failure that names the call and its error code
   */
  @ApiStatus.Internal
  fun processesUsingPath(path: Path): Result<List<ProcessHandle>> {
    assert(SystemInfoRt.isWindows) { "Do not call on **nix" }

    Arena.ofConfined().use { arena ->
      val callState = arena.allocate(WindowsKernel32.CALL_STATE)
      val name = arena.allocateFrom(path.pathString, StandardCharsets.UTF_16LE)
      val file = WindowsKernel32.createFile(
        callState,
        name,
        WindowsKernel32.FILE_READ_ATTRIBUTES,
        WindowsKernel32.FILE_SHARE_READ,
        WindowsKernel32.OPEN_EXISTING,
        if (path.isDirectory()) WindowsKernel32.FILE_FLAG_BACKUP_SEMANTICS else 0,
      )
      if (file.address() == WindowsKernel32.INVALID_HANDLE_VALUE) {
        return winFailure("CreateFileW failed for $path", WindowsKernel32.lastError(callState))
      }

      try {
        val statusBlock = arena.allocate(WindowsNtDll.IO_STATUS_BLOCK)
        var capacity = FIRST_CAPACITY
        while (true) {
          val layout = WindowsNtDll.fileProcessIds(capacity)
          val info = arena.allocate(layout)
          val status = WindowsNtDll.queryInformationFile(
            file, statusBlock, info, layout.byteSize().toInt(), WindowsNtDll.FILE_PROCESS_IDS_CLASS)

          // The status reports no required size, so the only strategy is a larger buffer.
          if (status == WindowsNtDll.STATUS_INFO_LENGTH_MISMATCH && capacity < MAX_CAPACITY) {
            capacity = capacity shl 1
            continue
          }
          if (status != 0) {
            return winFailure("NtQueryInformationFile failed for $path", status)
          }

          // The kernel fills the head of the list only, so read no further than the count it reports.
          val reported = Integer.toUnsignedLong(info.get(JAVA_INT, WindowsNtDll.NUMBER_OF_PROCESS_IDS_IN_LIST))
          val count = minOf(reported, capacity.toLong())
          val processes = (0 until count).mapNotNull { index ->
            val pid = info.get(JAVA_LONG, WindowsNtDll.PROCESS_ID_LIST + index * JAVA_LONG.byteSize())
            ProcessHandle.of(pid).getOrNull()
          }
          return Result.success(processes)
        }
      }
      finally {
        WindowsKernel32.closeHandle(file)
      }
    }
  }
}
