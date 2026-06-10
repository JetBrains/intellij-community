// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SpellCheckingInspection", "FunctionName", "ClassName")
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.openapi.vfs.impl.local.windows

import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.io.Closeable
import java.io.IOException
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.invoke.MethodHandle
import java.nio.ByteOrder
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

// Will be empty if won't get it
private val systemRoot: Path = Path(System.getenv("SystemRoot") ?: "/")

private enum class WindowsLibrary(val path: Path) {
  Kernel32(systemRoot / "System32" / "kernel32.dll"),
  Ntdll(systemRoot / "System32" / "ntdll.dll")
}

private enum class WindowsSymbol {
  CreateFileW,
  GetFileAttributesW,
  NtQueryDirectoryFile,
  CloseHandle,
  RtlNtStatusToDosError,
  DeviceIoControl
}

private val handleCache = mutableMapOf<Pair<WindowsLibrary, WindowsSymbol>, MethodHandle>()

private object WindowsDllLookup {
  private val kernel32DllLookup: SymbolLookup? = if (OS.CURRENT == OS.Windows) SymbolLookup.libraryLookup(WindowsLibrary.Kernel32.path, Arena.global()) else null
  private val ntdllDllLookup: SymbolLookup? = if (OS.CURRENT == OS.Windows) SymbolLookup.libraryLookup(WindowsLibrary.Ntdll.path, Arena.global()) else null

  private val libsLookups = mapOf(
    WindowsLibrary.Kernel32 to kernel32DllLookup,
    WindowsLibrary.Ntdll to ntdllDllLookup
  )

  fun handleFor(library: WindowsLibrary, label: WindowsSymbol): MethodHandle {
    return synchronized(handleCache) {
      handleCache.getOrPut(library to label) {
        val libraryLookup = libsLookups[library] ?: throw IllegalArgumentException("Library '$library' not defined as a lookupable library")
        val callStub = WindowsStubs[label] ?: throw IllegalArgumentException("Call stub '$label' not defined for library '$library'")
        val getLastErrorOption = Linker.Option.captureCallState("GetLastError")
        return@getOrPut Linker.nativeLinker().downcallHandle(libraryLookup.findOrThrow(label.name), callStub, getLastErrorOption)
      }
    }
  }
}

private object WindowsStubs {

  val canonicalLayouts: Map<String, MemoryLayout> = Linker.nativeLinker().canonicalLayouts()

  val HANDLE = canonicalLayouts["void*"]!!
  val LPCWSTR = canonicalLayouts["void*"]!!
  val LPVOID = canonicalLayouts["void*"]!!
  val LPSECURITY_ATTRIBUTES = canonicalLayouts["void*"]!!
  val DWORD = canonicalLayouts["long"]!!
  val BOOL = canonicalLayouts["int"]!!

  val WCHAR = canonicalLayouts["wchar_t"]!!
  val CHAR = canonicalLayouts["char"]!!

  val CreateFileW: FunctionDescriptor = FunctionDescriptor.of(
    HANDLE,  // return
    LPCWSTR.withName("lpFileName"), // _In_ LPCWSTR lpFileName,
    DWORD.withName("dwDesiredAccess"),   // _In_ DWORD dwDesiredAccess,
    DWORD.withName("dwShareMode"),   // _In_ DWORD dwShareMode,
    LPSECURITY_ATTRIBUTES.withName("lpSecurityAttributes"), //    _In_opt_ LPSECURITY_ATTRIBUTES lpSecurityAttributes,
    DWORD.withName("dwCreationDisposition"),   // _In_ DWORD dwCreationDisposition,
    DWORD.withName("dwFlagsAndAttributes"),   // _In_ DWORD dwFlagsAndAttributes,
    HANDLE.withName("hTemplateFile")   // _In_opt_ HANDLE hTemplateFile
  )

  val CloseHandle: FunctionDescriptor = FunctionDescriptor.of(
    BOOL,
    HANDLE.withName("hObject") // _In_ _Post_ptr_invalid_
  )

  val GetFileAttributesW: FunctionDescriptor = FunctionDescriptor.of(
    DWORD, // return
    LPCWSTR.withName("lpFileName") // _In_ LPCWSTR lpFileName
  )

  val NTSTATUS = canonicalLayouts["long"]!!
  val PIO_APC_ROUTINE = canonicalLayouts["void*"]!!
  val PVOID = canonicalLayouts["void*"]!!
  val PIO_STATUS_BLOCK = canonicalLayouts["void*"]!!
  val ULONG = canonicalLayouts["long"]!!
  val USHORT = canonicalLayouts["short"]!!
  val FILE_INFORMATION_CLASS = canonicalLayouts["int"]!!
  val BOOLEAN = canonicalLayouts["char"]!!
  val PUNICODE_STRING = canonicalLayouts["void*"]!!

  val NtQueryDirectoryFile: FunctionDescriptor = FunctionDescriptor.of(
    NTSTATUS, // return
    HANDLE.withName("FileHandle"), //	_In_
    HANDLE.withName("Event"),   //	_In_opt_
    PIO_APC_ROUTINE.withName("ApcRoutine"), //	_In_opt_
    PVOID.withName("ApcContext"), //	_In_opt_
    PIO_STATUS_BLOCK.withName("IoStatusBlock"), //	_Out_
    PVOID.withName("FileInformation"), //	_Out_writes_bytes_(Length)
    ULONG.withName("Length"), //	_In_
    FILE_INFORMATION_CLASS.withName("FileInformationClass"), //	_In_
    BOOLEAN.withName("ReturnSingleEntry"), //	_In_
    PUNICODE_STRING.withName("FileName"), //	_In_opt_
    BOOLEAN.withName("RestartScan") //	_In_
  )

  val LONGLONG = canonicalLayouts["long long"]!!

  // This is a variable length stucture
  // NextEntryOffset points into the same buffer; it is needed because the last field has dynamic len of FileNameLength
  val FILE_FULL_DIR_INFORMATION: MemoryLayout = MemoryLayout.structLayout(
    ULONG.withName("NextEntryOffset"),
    ULONG.withName("FileIndex"),
    LONGLONG.withName("CreationTime"),
    LONGLONG.withName("LastAccessTime"),
    LONGLONG.withName("LastWriteTime"),
    LONGLONG.withName("ChangeTime"),
    LONGLONG.withName("EndOfFile"),
    LONGLONG.withName("AllocationSize"),
    ULONG.withName("FileAttributes"),
    ULONG.withName("FileNameLength"),
    ULONG.withName("EaSize"),
    WCHAR.withName("FileName")
  )

  val REPARSE_DATA_BUFFER: MemoryLayout = MemoryLayout.structLayout(
    ULONG.withName("ReparseTag"), // ReparseTag
    USHORT.withName("ReparseDataLength"), // ReparseDataLength
    USHORT.withName("Reserved"), // Reserved
    CHAR.withName("Data"), // Data
  )

  val ULONG_PTR = canonicalLayouts["void*"]!!

  val IO_STATUS_BLOCK: MemoryLayout = MemoryLayout.structLayout(
    MemoryLayout.unionLayout(
      NTSTATUS.withName("Status"),
      PVOID.withName("Pointer")
    ),
    ULONG_PTR.withName("Information")
  )

  //NTSYSAPI ULONG RtlNtStatusToDosError(
  //  [in] NTSTATUS Status
  //);
  val RtlNtStatusToDosError: FunctionDescriptor = FunctionDescriptor.of(
    ULONG,
    NTSTATUS
  )

  val LPDWORD = canonicalLayouts["void*"]!!
  val LPOVERLAPPED = canonicalLayouts["void*"]!!

  val DeviceIoControl: FunctionDescriptor = FunctionDescriptor.of(
    BOOL, // return
    HANDLE, // _In_ hDevice
    DWORD, // _In_ dwIoControlCode
    LPVOID, // _In_reads_bytes_opt_(nInBufferSize) lpInBuffer
    DWORD, // _In_ nInBufferSize
    LPVOID, // _Out_writes_bytes_to_opt_(nOutBufferSize,*lpBytesReturned) lpOutBuffer
    DWORD, // _In_ nOutBufferSize
    LPDWORD, // _Out_opt_ lpBytesReturned
    LPOVERLAPPED, // _Inout_opt_ lpOverlapped
  )

  private val labelMap = mapOf(
    WindowsSymbol.CreateFileW to CreateFileW,
    WindowsSymbol.GetFileAttributesW to GetFileAttributesW,
    WindowsSymbol.NtQueryDirectoryFile to NtQueryDirectoryFile,
    WindowsSymbol.CloseHandle to CloseHandle,
    WindowsSymbol.RtlNtStatusToDosError to RtlNtStatusToDosError,
    WindowsSymbol.DeviceIoControl to DeviceIoControl,
  )

  operator fun get(label: WindowsSymbol): FunctionDescriptor? = labelMap[label]
}

// A Java `char` and a Windows `WCHAR` are both UTF-16 code units, so we copy them 1:1 as little-endian
// pairs instead of using a charset. This preserves unpaired surrogates (Windows names are WTF-16), which
// a UTF_16LE encoder/decoder would reject as malformed.
// NB: order(LITTLE_ENDIAN) must precede asCharBuffer() (the view captures the order); asByteBuffer() is BE.

@ApiStatus.Internal
fun toWinReadonlyCWSTR(arena: Arena, str: String): MemorySegment {
  // +1 code unit for the null terminator (arena memory is zero-initialized).
  val stringMemorySegment = arena.allocate(((str.length + 1) * 2).toLong(), 2L)
  stringMemorySegment.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN).asCharBuffer().put(str)
  return stringMemorySegment
}

@ApiStatus.Internal
fun toJavaStringFromWinCWSTR(segment: MemorySegment, length: Int): String {
  val charArray = CharArray(length)
  segment.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN).asCharBuffer().get(charArray)
  return String(charArray)
}

@Suppress("ConstPropertyName")
internal class Windows(val arena: Arena) : Closeable {

  companion object {
    val NULL: MemorySegment = MemorySegment.NULL
  }

  object Error {
    private const val ERROR_FILE_NOT_FOUND: Int = 2
    private const val ERROR_PATH_NOT_FOUND: Int = 3
    private const val ERROR_ACCESS_DENIED: Int = 5
    private const val ERROR_DIRECTORY: Int = 267

    // Maps a Win32 error code to the matching java.nio.file exception, mimicing the JDK's sun.nio.fs.WindowsException.
    fun win32ErrorToIOException(error: Int, path: Path): IOException {
      val file = path.toString()
      return when (error) {
        ERROR_FILE_NOT_FOUND, ERROR_PATH_NOT_FOUND -> NoSuchFileException(file)
        ERROR_ACCESS_DENIED -> AccessDeniedException(file)
        ERROR_DIRECTORY -> NotDirectoryException(file)
        else -> FileSystemException(file, null, "Windows error code: $error")
      }
    }
  }

  object FileOperations {
    const val FILE_LIST_DIRECTORY: Int = 0x1
    const val FILE_GENERIC_READ: Int = 0x80000000.toInt()
    const val FILE_FLAG_BACKUP_SEMANTICS: Int = 0x02000000
    const val FILE_OPEN_REPARSE_POINT: Int = 0x00200000
  }

  object FileMode {
    const val OPEN_EXISTING: Int = 0x3
  }

  object FileShare {
    const val FILE_SHARE_READWRITE: Int = 0x3
    const val FILE_SHARE_DELETE: Int = 0x4
  }

  object NtStatus {
    const val STATUS_NO_MORE_FILES: Int = 0x80000006.toInt()
    const val STATUS_SUCCESS: Int = 0x0
    const val STATUS_FILE_NOT_FOUND: Int = 0xC000000F.toInt()
  }

  object FILE_FULL_DIR_INFORMATION {
    const val FILE_FULL_DIRECTORY_INFORMATION: Int = 2
  }

  object DeviceIoControlCodes {
    const val FSCTL_GET_REPARSE_POINT: UInt = 0x000900A8U
  }

  object Read {
    fun HANDLE(segment: MemorySegment): Long {
      return segment.address()
    }

    object IO_STATUS_BLOCK {
      private val informationHandle = WindowsStubs.IO_STATUS_BLOCK.varHandle(MemoryLayout.PathElement.groupElement("Information"))

      fun Information(memorySegment: MemorySegment): Long {
        return (informationHandle.get(memorySegment, 0L) as MemorySegment).address()
      }
    }

    object FILE_FULL_DIR_INFORMATION {
      private val nextEntryOffsetHandle = WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("NextEntryOffset"))
      private val fileNameLengthHandle = WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("FileNameLength"))
      private val creationTimeHandle = WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("CreationTime"))
      private val lastAccessTimeHandle = WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("LastAccessTime"))
      private val lastWriteTimeHandle = WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("LastWriteTime"))
      private val fileAttributesHandle = WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("FileAttributes"))
      private val endOfFileHandle = WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("EndOfFile"))

      fun fetchNextInBuffer(current: MemorySegment, buffer: MemorySegment): MemorySegment {
        if (current == NULL) return NULL
        val offset = NextEntryOffset(current)
        if (offset == 0) return NULL
        return buffer.asSlice((current.address() - buffer.address()) + offset.toLong(), WindowsStubs.FILE_FULL_DIR_INFORMATION)
      }

      fun fetchFirstInBuffer(buffer: MemorySegment): MemorySegment {
        return buffer.asSlice(0, WindowsStubs.FILE_FULL_DIR_INFORMATION)
      }

      fun NextEntryOffset(buffer: MemorySegment): Int {
        return nextEntryOffsetHandle.get(buffer, 0L) as Int
      }

      fun FileNameLength(buffer: MemorySegment): Int {
        return fileNameLengthHandle.get(buffer, 0L) as Int
      }

      // buffer should start with current
      fun fetchFileNameSegment(strLen: Int, current: MemorySegment, buffer: MemorySegment): MemorySegment {
        val startOffset = WindowsStubs.FILE_FULL_DIR_INFORMATION.byteOffset(MemoryLayout.PathElement.groupElement("FileName"))
        return buffer.asSlice(current.address() - buffer.address() + startOffset, strLen.toLong())
      }

      fun CreationTime(buffer: MemorySegment): Long {
        return creationTimeHandle.get(buffer, 0L) as Long
      }

      fun LastAccessTime(buffer: MemorySegment): Long {
        return lastAccessTimeHandle.get(buffer, 0L) as Long
      }

      fun LastWriteTime(buffer: MemorySegment): Long {
        return lastWriteTimeHandle.get(buffer, 0L) as Long
      }

      fun FileAttributes(buffer: MemorySegment): Int {
        return fileAttributesHandle.get(buffer, 0L) as Int
      }

      fun EndOfFile(buffer: MemorySegment): Long {
        return endOfFileHandle.get(buffer, 0L) as Long
      }
    }

    object REPARSE_DATA_BUFFER {
      private val reparseTagHandle = WindowsStubs.REPARSE_DATA_BUFFER.varHandle(MemoryLayout.PathElement.groupElement("ReparseTag"))

      fun ReparseTag(buffer: MemorySegment): Int {
        return reparseTagHandle.get(buffer, 0L) as Int
      }

      object Tag {
        const val IO_REPARSE_TAG_SYMLINK: Int = 0xA000000C.toInt()
      }
    }
  }

  object Alloc {
    fun IO_STATUS_BLOCK(arena: Arena): MemorySegment {
      return arena.allocate(WindowsStubs.IO_STATUS_BLOCK)
    }

    fun ByteBuffer(arena: Arena, size: Long): MemorySegment {
      return arena.allocate(WindowsStubs.CHAR, size)
    }
  }

  object FileAttributes {
    const val None: Int = 0x0000
    const val ReadOnly: Int = 0x0001
    const val Hidden: Int = 0x0002
    const val System: Int = 0x0004
    const val Directory: Int = 0x0010
    const val Archive: Int = 0x0020
    const val Device: Int = 0x0040
    const val Normal: Int = 0x0080
    const val Temporary: Int = 0x0100
    const val SparseFile: Int = 0x0200
    const val ReparsePoint: Int = 0x0400
    const val Compressed: Int = 0x0800
    const val Offline: Int = 0x1000
    const val NotContentIndexed: Int = 0x2000
    const val Encrypted: Int = 0x4000
    const val IntegrityStream: Int = 0x8000
    const val NoScrubData: Int = 0x20000

    // GetFileAttributesW returns this (0xFFFFFFFF) when the file/directory cannot be queried.
    const val INVALID_FILE_ATTRIBUTES: Int = 0xFFFFFFFF.toInt()
  }

  private val capturedStateLayout = Linker.Option.captureStateLayout()
  private val handleGetLastError = capturedStateLayout.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"))

  private val errorMemorySegment: ThreadLocal<MemorySegment> = ThreadLocal.withInitial { arena.allocate(capturedStateLayout) }

  fun GetLastFFIError(): Int {
     return handleGetLastError.get(errorMemorySegment.get(), 0L) as Int
  }

  fun CreateFileW(
    fileName: String,
    dwDesiredAccess: Int,
    dwShareMode: Int,
    lpSecurityAttributes: MemorySegment,
    dwCreationDisposition: Int,
    dwFlagsAndAttributes: Int,
    hTemplateFile: MemorySegment,
  ): MemorySegment {
    Arena.ofConfined().use {
      return WindowsDllLookup.handleFor(WindowsLibrary.Kernel32, WindowsSymbol.CreateFileW).invokeExact(
        errorMemorySegment.get(),
        toWinReadonlyCWSTR(it, fileName),
        dwDesiredAccess,
        dwShareMode,
        lpSecurityAttributes,
        dwCreationDisposition,
        dwFlagsAndAttributes,
        hTemplateFile
      ) as MemorySegment
    }
  }

  fun NtQueryDirectoryFile(
    fileHandle: MemorySegment,
    event: MemorySegment,
    apcRoutine: MemorySegment,
    apcContext: MemorySegment,
    ioStatusBlock: MemorySegment,
    fileInformation: MemorySegment,
    length: Int,
    fileInformationClass: Int,
    returnSingleEntry: Byte,
    fileName: MemorySegment,
    restartScan: Byte,
  ): Int {
    return WindowsDllLookup.handleFor(WindowsLibrary.Ntdll, WindowsSymbol.NtQueryDirectoryFile).invokeExact(
      errorMemorySegment.get(),
      fileHandle,
      event,
      apcRoutine,
      apcContext,
      ioStatusBlock,
      fileInformation,
      length,
      fileInformationClass,
      returnSingleEntry,
      fileName,
      restartScan
    ) as Int
  }

  fun CloseHandle(handle: MemorySegment): Int {
    return WindowsDllLookup.handleFor(WindowsLibrary.Kernel32, WindowsSymbol.CloseHandle).invokeExact(
      errorMemorySegment.get(),
      handle
    ) as Int
  }

  fun GetFileAttributesW(fileName: String): Int {
    Arena.ofConfined().use {
      return WindowsDllLookup.handleFor(WindowsLibrary.Kernel32, WindowsSymbol.GetFileAttributesW).invokeExact(
        errorMemorySegment.get(),
        toWinReadonlyCWSTR(it, fileName)
      ) as Int
    }
  }

  fun RtlNtStatusToDosError(ntStatus: Int): Int {
    return WindowsDllLookup.handleFor(WindowsLibrary.Ntdll, WindowsSymbol.RtlNtStatusToDosError).invokeExact(
      errorMemorySegment.get(),
      ntStatus
    ) as Int
  }

  fun DeviceIoControl(
    handle: MemorySegment,
    dwIoControlCode: Int,
    lpInBuffer: MemorySegment,
    nInBufferSize: Int,
    lpOutBuffer: MemorySegment,
    nOutBufferSize: Int,
    lpBytesReturned: MemorySegment,
    lpOverlapped: MemorySegment
  ): Int {
    return WindowsDllLookup.handleFor(WindowsLibrary.Kernel32, WindowsSymbol.DeviceIoControl).invokeExact(
      errorMemorySegment.get(),
      handle,
      dwIoControlCode,
      lpInBuffer,
      nInBufferSize,
      lpOutBuffer,
      nOutBufferSize,
      lpBytesReturned,
      lpOverlapped
    ) as Int
  }

  override fun close() {
    arena.close()
  }
}