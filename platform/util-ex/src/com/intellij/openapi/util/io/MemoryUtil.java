// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Memory statistics of the local macOS host, read with Mach downcalls.
 * It lives here, and not in {@code intellij.platform.util}, because that module stays at Java 8 and cannot use the FFM API.
 */
@ApiStatus.Internal
public final class MemoryUtil {
  private MemoryUtil() { }

  /**
   * {@code struct vm_statistics64} from {@code <mach/vm_statistics.h>}: the first four fields are {@code natural_t} page counts,
   * the rest are 64-bit counters and further page counts. The layout is public for the layout test only.
   */
  public static final StructLayout VM_STATISTICS64 = MemoryLayout.structLayout(
    JAVA_INT.withName("free_count"),
    JAVA_INT.withName("active_count"),
    JAVA_INT.withName("inactive_count"),
    JAVA_INT.withName("wire_count"),
    JAVA_LONG.withName("zero_fill_count"),
    JAVA_LONG.withName("reactivations"),
    JAVA_LONG.withName("pageins"),
    JAVA_LONG.withName("pageouts"),
    JAVA_LONG.withName("faults"),
    JAVA_LONG.withName("cow_faults"),
    JAVA_LONG.withName("lookups"),
    JAVA_LONG.withName("hits"),
    JAVA_LONG.withName("purges"),
    JAVA_INT.withName("purgeable_count"),
    JAVA_INT.withName("speculative_count"),
    JAVA_LONG.withName("decompressions"),
    JAVA_LONG.withName("compressions"),
    JAVA_LONG.withName("swapins"),
    JAVA_LONG.withName("swapouts"),
    JAVA_INT.withName("compressor_page_count"),
    JAVA_INT.withName("throttled_count"),
    JAVA_INT.withName("external_page_count"),
    JAVA_INT.withName("internal_page_count"),
    JAVA_LONG.withName("total_uncompressed_pages_in_compressor")
  );
  private static final long FREE_COUNT_OFFSET = VM_STATISTICS64.byteOffset(MemoryLayout.PathElement.groupElement("free_count"));
  private static final long INACTIVE_COUNT_OFFSET = VM_STATISTICS64.byteOffset(MemoryLayout.PathElement.groupElement("inactive_count"));

  private static final int KERN_SUCCESS = 0;
  private static final int HOST_VM_INFO64 = 2;

  /**
   * Retrieves the amount of unused memory on macOS systems.
   * We can't use mxBean for macOS because it considers cache as used memory, which can confuse users since the used memory will always appear to be around 99%.
   * <p>
   * The value is the sum of the free and the inactive page counts of {@code host_statistics64}, times the page size.
   *
   * @return the unused memory in bytes, or {@code null} off macOS and when a Mach call fails
   */
  public static @Nullable Long getUnusedMemory() {
    if (!SystemInfo.isMac) return null;
    try (Arena arena = Arena.ofConfined()) {
      int host = (int)Mach.MACH_HOST_SELF.invokeExact();
      MemorySegment pageSize = arena.allocate(JAVA_LONG);
      if ((int)Mach.HOST_PAGE_SIZE.invokeExact(host, pageSize) != KERN_SUCCESS) {
        return null;
      }
      MemorySegment statistics = arena.allocate(VM_STATISTICS64);
      MemorySegment count = arena.allocate(JAVA_INT);
      count.set(JAVA_INT, 0, (int)(VM_STATISTICS64.byteSize() / JAVA_INT.byteSize()));
      if ((int)Mach.HOST_STATISTICS64.invokeExact(host, HOST_VM_INFO64, statistics, count) != KERN_SUCCESS) {
        return null;
      }
      long freePages = Integer.toUnsignedLong(statistics.get(JAVA_INT, FREE_COUNT_OFFSET));
      long inactivePages = Integer.toUnsignedLong(statistics.get(JAVA_INT, INACTIVE_COUNT_OFFSET));
      return (freePages + inactivePages) * pageSize.get(JAVA_LONG, 0);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * Downcalls into {@code libSystem}, the default lookup on macOS. {@code host_t}, {@code kern_return_t} and
   * {@code mach_msg_type_number_t} are {@code int}, {@code vm_size_t} is {@code long}.
   */
  private static final class Mach {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = LINKER.defaultLookup();

    static final MethodHandle MACH_HOST_SELF = downcall("mach_host_self", FunctionDescriptor.of(JAVA_INT));
    static final MethodHandle HOST_PAGE_SIZE = downcall("host_page_size", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));
    static final MethodHandle HOST_STATISTICS64 = downcall("host_statistics64", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
      return LINKER.downcallHandle(LOOKUP.findOrThrow(name), descriptor);
    }
  }
}
