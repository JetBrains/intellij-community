// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * The {@code ntdll.dll} downcalls and the NT structure layouts. Windows only: the first call loads the library.
 * <p>
 * Each layout is for a 64-bit target, so a {@code ULONG_PTR} takes 8 bytes. A layout names only the fields that a
 * caller reads. A padding member holds the place of every other field, so each named field keeps the offset of the
 * Windows header. {@code WindowsNtDllLayoutTest} pins each size and each offset.
 * <p>
 * A layout needs no library, so a test can read it on any operating system. A downcall loads {@code ntdll.dll}.
 */
@ApiStatus.Internal
public final class WindowsNtDll {
  private WindowsNtDll() { }

  /** {@code FILE_INFORMATION_CLASS::FileProcessIdsUsingFileInformation} from {@code wdm.h} */
  public static final int FILE_PROCESS_IDS_CLASS = 47;

  /** {@code PROCESSINFOCLASS::ProcessBasicInformation} from {@code winternl.h} */
  public static final int PROCESS_BASIC_INFORMATION_CLASS = 0;

  /** {@code STATUS_INFO_LENGTH_MISMATCH}: the buffer is too small. The status reports no required size. */
  public static final int STATUS_INFO_LENGTH_MISMATCH = 0xC0000004;

  /**
   * {@code UNICODE_STRING { USHORT Length; USHORT MaximumLength; PWSTR Buffer; }}, 16 bytes.
   * {@code Length} counts bytes, not characters, and it excludes the terminator.
   */
  public static final StructLayout UNICODE_STRING = MemoryLayout.structLayout(
    JAVA_SHORT.withName("Length"),
    JAVA_SHORT.withName("MaximumLength"),
    MemoryLayout.paddingLayout(4),
    ADDRESS.withName("Buffer"));

  public static final long UNICODE_STRING_LENGTH = UNICODE_STRING.byteOffset(groupElement("Length"));
  public static final long UNICODE_STRING_BUFFER = UNICODE_STRING.byteOffset(groupElement("Buffer"));

  /**
   * {@code IO_STATUS_BLOCK}, 16 bytes: a union of {@code NTSTATUS Status} and {@code PVOID Pointer}, then
   * {@code ULONG_PTR Information}. The two arms of the union overlap at offset 0, so one pointer covers both.
   */
  public static final StructLayout IO_STATUS_BLOCK = MemoryLayout.structLayout(
    ADDRESS.withName("Pointer"),
    JAVA_LONG.withName("Information"));

  public static final long IO_STATUS_INFORMATION = IO_STATUS_BLOCK.byteOffset(groupElement("Information"));

  /**
   * {@code PROCESS_BASIC_INFORMATION} from {@code winternl.h}, 48 bytes. The padding holds {@code ExitStatus},
   * {@code AffinityMask}, {@code BasePriority} and {@code UniqueProcessId}.
   * <p>
   * The size must stay 48. {@code NtQueryInformationProcess} compares {@code ProcessInformationLength} against the
   * full structure, and answers {@link #STATUS_INFO_LENGTH_MISMATCH} for a shorter buffer.
   */
  public static final StructLayout PROCESS_BASIC_INFORMATION = MemoryLayout.structLayout(
    MemoryLayout.paddingLayout(8),
    ADDRESS.withName("PebBaseAddress"),
    MemoryLayout.paddingLayout(24),
    JAVA_LONG.withName("InheritedFromUniqueProcessId"));

  public static final long PEB_BASE_ADDRESS = PROCESS_BASIC_INFORMATION.byteOffset(groupElement("PebBaseAddress"));
  public static final long INHERITED_FROM_UNIQUE_PROCESS_ID =
    PROCESS_BASIC_INFORMATION.byteOffset(groupElement("InheritedFromUniqueProcessId"));

  /**
   * The head of {@code PEB} from {@code winternl.h}, 40 bytes. The padding holds {@code Reserved1[2]},
   * {@code BeingDebugged}, {@code Reserved2[1]}, {@code Reserved3[2]} and {@code Ldr}.
   * <p>
   * The real {@code PEB} is much longer. A reader copies this head only, which stays correct for one field.
   */
  public static final StructLayout PEB = MemoryLayout.structLayout(
    MemoryLayout.paddingLayout(32),
    ADDRESS.withName("ProcessParameters"));

  public static final long PROCESS_PARAMETERS = PEB.byteOffset(groupElement("ProcessParameters"));

  /**
   * The head of {@code RTL_USER_PROCESS_PARAMETERS} from {@code winternl.h}, 128 bytes. The padding holds
   * {@code Reserved1[16]} and {@code Reserved2[10]}. The real structure continues past this head.
   */
  public static final StructLayout RTL_USER_PROCESS_PARAMETERS = MemoryLayout.structLayout(
    MemoryLayout.paddingLayout(96),
    UNICODE_STRING.withName("ImagePathName"),
    UNICODE_STRING.withName("CommandLine"));

  public static final long IMAGE_PATH_NAME = RTL_USER_PROCESS_PARAMETERS.byteOffset(groupElement("ImagePathName"));
  public static final long COMMAND_LINE = RTL_USER_PROCESS_PARAMETERS.byteOffset(groupElement("CommandLine"));

  /**
   * {@code FILE_PROCESS_IDS_USING_FILE_INFORMATION}: a {@code ULONG NumberOfProcessIdsInList}, then a
   * {@code ULONG_PTR ProcessIdList[]} of a variable length. The caller gives the capacity.
   *
   * @param capacity the number of {@code ULONG_PTR} entries that {@code ProcessIdList} holds
   */
  @ApiStatus.Internal
  public static @NotNull StructLayout fileProcessIds(int capacity) {
    return MemoryLayout.structLayout(
      JAVA_INT.withName("NumberOfProcessIdsInList"),
      MemoryLayout.paddingLayout(4),
      MemoryLayout.sequenceLayout(capacity, JAVA_LONG).withName("ProcessIdList"));
  }

  /** The layout with one entry, 16 bytes. The two offsets below hold for every capacity. */
  public static final StructLayout FILE_PROCESS_IDS = fileProcessIds(1);

  public static final long NUMBER_OF_PROCESS_IDS_IN_LIST = FILE_PROCESS_IDS.byteOffset(groupElement("NumberOfProcessIdsInList"));
  public static final long PROCESS_ID_LIST = FILE_PROCESS_IDS.byteOffset(groupElement("ProcessIdList"));

  /**
   * The {@code ntdll.dll} lookup, for a symbol that only one class needs. A symbol that more than one class
   * needs belongs in this class.
   */
  @ApiStatus.Internal
  public static @NotNull SymbolLookup ntdll() {
    return Handles.NTDLL;
  }

  /**
   * {@code NTSTATUS NtQueryInformationFile(HANDLE file, PIO_STATUS_BLOCK, PVOID information, ULONG length,
   * FILE_INFORMATION_CLASS)}
   *
   * @return the {@code NTSTATUS}, where 0 reports success
   */
  @ApiStatus.Internal
  public static int queryInformationFile(
    @NotNull MemorySegment file, @NotNull MemorySegment statusBlock, @NotNull MemorySegment information, int length, int informationClass
  ) {
    try {
      return (int)Handles.QUERY_INFORMATION_FILE.invokeExact(file, statusBlock, information, length, informationClass);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * {@code NTSTATUS NtQueryInformationProcess(HANDLE process, PROCESSINFOCLASS, PVOID information, ULONG length,
   * PULONG returnLength)}, without the returned length.
   *
   * @return the {@code NTSTATUS}, where 0 reports success
   */
  @ApiStatus.Internal
  public static int queryInformationProcess(
    @NotNull MemorySegment process, int informationClass, @NotNull MemorySegment information, int length
  ) {
    try {
      return (int)Handles.QUERY_INFORMATION_PROCESS.invokeExact(process, informationClass, information, length, MemorySegment.NULL);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** The library and the handles load on the first call, so the layouts above stay readable on another operating system. */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    static final SymbolLookup NTDLL = WindowsSystemLibraries.lookup("ntdll.dll");

    static final MethodHandle QUERY_INFORMATION_FILE = LINKER.downcallHandle(
      NTDLL.findOrThrow("NtQueryInformationFile"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

    static final MethodHandle QUERY_INFORMATION_PROCESS = LINKER.downcallHandle(
      NTDLL.findOrThrow("NtQueryInformationProcess"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
  }
}
