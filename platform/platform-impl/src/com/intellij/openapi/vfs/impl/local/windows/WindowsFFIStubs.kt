// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SpellCheckingInspection")

package com.intellij.openapi.vfs.impl.local.windows

import com.intellij.openapi.vfs.impl.local.windows.WindowsStubs.BOOL
import com.intellij.openapi.vfs.impl.local.windows.WindowsStubs.DWORD
import com.intellij.openapi.vfs.impl.local.windows.WindowsStubs.HANDLE
import com.intellij.openapi.vfs.impl.local.windows.WindowsStubs.LPDWORD
import com.intellij.openapi.vfs.impl.local.windows.WindowsStubs.LPOVERLAPPED
import com.intellij.openapi.vfs.impl.local.windows.WindowsStubs.LPVOID
import java.io.Closeable
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.invoke.MethodHandle
import java.lang.invoke.VarHandle
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CoderResult
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

// Will be empty if won't get it
private val systemRoot: Path = Path(System.getenv("SystemRoot"))

enum class WindowsLibrary(val path: Path) {
  Kernel32(systemRoot / "System32" / "kernel32.dll"),
  Ntdll(systemRoot / "System32" / "ntdll.dll")
}

enum class WindowsSymbol {
  CreateFileW,
  NtCreateFile,
  NtQueryDirectoryFile,
  CloseHandle,
  RtlNtStatusToDosError,
  DeviceIoControl
}

private class WindowsDllLookup(arena: Arena) {

  private val kernel32DllLookup: SymbolLookup = SymbolLookup.libraryLookup(WindowsLibrary.Kernel32.path, arena)
  private val ntdllDllLookup: SymbolLookup = SymbolLookup.libraryLookup(WindowsLibrary.Ntdll.path, arena)

  private val libsLookups = mapOf(
    WindowsLibrary.Kernel32 to kernel32DllLookup,
    WindowsLibrary.Ntdll to ntdllDllLookup
  )

  fun handleFor(library: WindowsLibrary, label: WindowsSymbol): MethodHandle {
    val libraryLookup = libsLookups[library] ?: throw IllegalArgumentException("Library '$library' not defined as a lookupable library")
    val callStub = WindowsStubs[label] ?: throw IllegalArgumentException("Call stub '$label' not defined for library '$library'")
    val getLastErrorOption = Linker.Option.captureCallState("GetLastError")
    return Linker.nativeLinker().downcallHandle(libraryLookup.findOrThrow(label.name), callStub, getLastErrorOption)
  }
}

@Suppress("PropertyName")
internal object WindowsStubs {

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

  val PHANDLE = canonicalLayouts["void*"]!!
  val ACCESS_MASK = canonicalLayouts["long"]!!
  val POBJECT_ATTRIBUTES = canonicalLayouts["void*"]!!
  val PLARGE_INTEGER = canonicalLayouts["void*"]!!

  val NtCreateFile: FunctionDescriptor = FunctionDescriptor.of(
    NTSTATUS,
    PHANDLE, // _Out_ FileHandle
    ACCESS_MASK, // _In_ DesiredAccess
    POBJECT_ATTRIBUTES, // _In_ ObjectAttributes
    PIO_STATUS_BLOCK, // _Out_ IoStatusBlock
    PLARGE_INTEGER, // _In_opt_ AllocationSize
    ULONG, // _Out_ FileAttributes
    ULONG, // _In_ ShareAccess
    ULONG, // _In_ CreateDisposition
    ULONG, // _In_ CreateOptions
    PVOID, // _In_ EaBuffer
    ULONG // _In_ EaLength
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

  val IO_STATUS_BLOCK: MemoryLayout = MemoryLayout.structLayout(
    MemoryLayout.unionLayout(
      NTSTATUS.withName("Status"),
      PVOID.withName("Pointer")
    ),
    ULONG.withName("Information")
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
    WindowsSymbol.NtQueryDirectoryFile to NtQueryDirectoryFile,
    WindowsSymbol.CloseHandle to CloseHandle,
    WindowsSymbol.RtlNtStatusToDosError to RtlNtStatusToDosError,
    WindowsSymbol.DeviceIoControl to DeviceIoControl,
    WindowsSymbol.NtCreateFile to NtCreateFile,
  )

  operator fun get(label: WindowsSymbol): FunctionDescriptor? = labelMap[label]
}

private val UTF16_ENCODER = StandardCharsets.UTF_16LE.newEncoder()
private val UTF16_DECODER = StandardCharsets.UTF_16LE.newDecoder()

fun toWinReadonlyCWSTR(arena: Arena, str: String): MemorySegment {
  val stringMemorySegment = arena.allocate(((str.length + 1) * 2).toLong(), 2L)!!
  val buffer = stringMemorySegment.asByteBuffer()!!

  val coderResult = UTF16_ENCODER.encode(CharBuffer.wrap(str), buffer, true)
  if (coderResult != CoderResult.UNDERFLOW)
    throw IllegalArgumentException("Wrong byte array lenght ${(str.length + 1) * 2} for coding java string length ${str.length}!")

  buffer.put(str.length * 2, 0)
  buffer.put(str.length * 2 + 1, 0)

  return stringMemorySegment
}

fun toJavaStringFromWinCWSTR(segment: MemorySegment, length: Int): String {
  val charArray = CharArray(length)
  val charBuffer = CharBuffer.wrap(charArray)

  val coderResult = UTF16_DECODER.decode(segment.asByteBuffer(), charBuffer, true)
  if (coderResult != CoderResult.UNDERFLOW)
    throw IllegalArgumentException("Wrong $length for decoding a null-terminated string into a java string!")

  return String(charArray)
}

class Windows(val arena: Arena) : Closeable {

  companion object {
    val NULL: MemorySegment = MemorySegment.NULL
  }

  object FileOperations {
    const val FILE_LIST_DIRECTORY: UInt = 0x1U
    const val FILE_GENERIC_READ: UInt = 0x80000000U
    const val FILE_FLAG_BACKUP_SEMANTICS: UInt = 0x02000000U
    const val FILE_OPEN_REPARSE_POINT: UInt = 0x00200000U
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
    const val FILE_FULL_DIRECTORY_INFORMATION = 2
  }

  object DeviceIoControlCodes {
    const val FSCTL_GET_REPARSE_POINT = 0x000900A8U
  }

  object Read {
    fun HANDLE(segment: MemorySegment): Long {
      return segment.address()
    }

    object IO_STATUS_BLOCK {
      fun Information(memorySegment: MemorySegment): Long {
        return WindowsStubs.IO_STATUS_BLOCK.varHandle(MemoryLayout.PathElement.groupElement("Information")).get(memorySegment, 0L) as Long
      }
    }

    object FILE_FULL_DIR_INFORMATION {
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
        return WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("NextEntryOffset")).get(buffer, 0L) as Int
      }

      fun FileNameLength(buffer: MemorySegment): Long {
        return WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("FileNameLength")).get(buffer, 0L) as Long
      }

      // buffer should start with current
      fun fetchFileNameSegment(strLen: Long, current: MemorySegment, buffer: MemorySegment): MemorySegment {
        val startOffset = WindowsStubs.FILE_FULL_DIR_INFORMATION.byteOffset(MemoryLayout.PathElement.groupElement("FileName"))
        return buffer.asSlice(current.address() - buffer.address() + startOffset, strLen)
      }

      fun CreationTime(buffer: MemorySegment): Long {
        return WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("CreationTime")).get(buffer, 0L) as Long
      }

      fun LastAccessTime(buffer: MemorySegment): Long {
        return WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("LastAccessTime")).get(buffer, 0L) as Long
      }

      fun LastWriteTime(buffer: MemorySegment): Long {
        return WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("LastWriteTime")).get(buffer, 0L) as Long
      }

      fun FileAttributes(buffer: MemorySegment): Int {
        return WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("FileAttributes")).get(buffer, 0L) as Int
      }

      fun EndOfFile(buffer: MemorySegment): Long {
        return WindowsStubs.FILE_FULL_DIR_INFORMATION.varHandle(MemoryLayout.PathElement.groupElement("EndOfFile")).get(buffer, 0L) as Long
      }
    }

    object REPARSE_DATA_BUFFER {
      fun ReparseTag(buffer: MemorySegment): Long {
        return WindowsStubs.REPARSE_DATA_BUFFER.varHandle(MemoryLayout.PathElement.groupElement("ReparseTag")).get(buffer, 0L) as Long
      }

      object Tag {
        const val IO_REPARSE_TAG_SYMLINK = 0xA000000C
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
    const val None = 0x0000
    const val ReadOnly = 0x0001
    const val Hidden = 0x0002
    const val System = 0x0004
    const val Directory = 0x0010
    const val Archive = 0x0020
    const val Device = 0x0040
    const val Normal = 0x0080
    const val Temporary = 0x0100
    const val SparseFile = 0x0200
    const val ReparsePoint = 0x0400
    const val Compressed = 0x0800
    const val Offline = 0x1000
    const val NotContentIndexed = 0x2000
    const val Encrypted = 0x4000
    const val IntegrityStream = 0x8000
    const val NoScrubData = 0x20000
  }

  private val dllLookup = WindowsDllLookup(arena)

  private val capturedStateLayout = Linker.Option.captureStateLayout()
  private val handleGetLastError = capturedStateLayout.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"))

  private val errorMemorySegment: ThreadLocal<MemorySegment> = ThreadLocal.withInitial { arena.allocate(capturedStateLayout) }

  fun GetLastFFIError(): Int {
     return handleGetLastError.get(errorMemorySegment.get(), 0L) as Int
  }

  fun CreateFileW(
    fileName: String,
    dwDesiredAccess: UInt,
    dwShareMode: Int,
    lpSecurityAttributes: MemorySegment,
    dwCreationDisposition: Int,
    dwFlagsAndAttributes: UInt,
    hTemplateFile: MemorySegment,
  ): MemorySegment {
    Arena.ofConfined().use {
      return dllLookup.handleFor(WindowsLibrary.Kernel32, WindowsSymbol.CreateFileW).invokeExact(
        errorMemorySegment.get(),
        toWinReadonlyCWSTR(it, fileName),
        dwDesiredAccess.toInt(),
        dwShareMode,
        lpSecurityAttributes,
        dwCreationDisposition,
        dwFlagsAndAttributes.toInt(),
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
    return dllLookup.handleFor(WindowsLibrary.Ntdll, WindowsSymbol.NtQueryDirectoryFile).invokeExact(
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
    return dllLookup.handleFor(WindowsLibrary.Kernel32, WindowsSymbol.CloseHandle).invokeExact(
      errorMemorySegment.get(),
      handle
    ) as Int
  }

  fun RtlNtStatusToDosError(ntStatus: Int): Int {
    return dllLookup.handleFor(WindowsLibrary.Ntdll, WindowsSymbol.RtlNtStatusToDosError).invokeExact(
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
    return dllLookup.handleFor(WindowsLibrary.Kernel32, WindowsSymbol.DeviceIoControl).invokeExact(
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