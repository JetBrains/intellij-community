// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.fus;

import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** The macOS machine id, read from the I/O Registry with IOKit and CoreFoundation downcalls. macOS only: the first call loads both frameworks. */
final class MachineIdNative {
  private MachineIdNative() { }

  private static final int KCF_STRING_ENCODING_UTF8 = 0x08000100;
  private static final long UUID_BUFFER_SIZE = 256;

  /**
   * See <a href="https://developer.apple.com/documentation/kernel/ioplatformexpertdevice">IOPlatformExpertDevice</a>.
   *
   * @return the {@code IOPlatformUUID} property of {@code IOPlatformExpertDevice}, or {@code null} when the registry has none
   */
  static @Nullable String macOsPlatformUuid() {
    try (Arena arena = Arena.ofConfined()) {
      // IOServiceGetMatchingService consumes one reference of the matching dictionary.
      MemorySegment matching = (MemorySegment)IOKit.IO_SERVICE_MATCHING.invokeExact(arena.allocateFrom("IOPlatformExpertDevice"));
      int service = (int)IOKit.IO_SERVICE_GET_MATCHING_SERVICE.invokeExact(0, matching);
      if (service == 0) {
        return null;
      }
      try {
        MemorySegment key = (MemorySegment)IOKit.CF_STRING_CREATE_WITH_C_STRING.invokeExact(
          MemorySegment.NULL, arena.allocateFrom("IOPlatformUUID"), KCF_STRING_ENCODING_UTF8);
        try {
          MemorySegment uuid = (MemorySegment)IOKit.IO_REGISTRY_ENTRY_CREATE_CF_PROPERTY.invokeExact(service, key, MemorySegment.NULL, 0);
          if (uuid.equals(MemorySegment.NULL)) {
            return null;
          }
          try {
            MemorySegment buffer = arena.allocate(UUID_BUFFER_SIZE);
            byte copied = (byte)IOKit.CF_STRING_GET_C_STRING.invokeExact(uuid, buffer, UUID_BUFFER_SIZE, KCF_STRING_ENCODING_UTF8);
            return copied != 0 ? buffer.getString(0) : null;
          }
          finally {
            IOKit.CF_RELEASE.invokeExact(uuid);
          }
        }
        finally {
          IOKit.CF_RELEASE.invokeExact(key);
        }
      }
      finally {
        int ignored = (int)IOKit.IO_OBJECT_RELEASE.invokeExact(service);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * Downcalls into the IOKit and CoreFoundation frameworks. Both live in the dyld shared cache, so the {@code String} overload of
   * {@code libraryLookup} is used: it goes through {@code dlopen}, and the {@code Path} overload rejects a path with no file behind it.
   * {@code io_service_t} and {@code mach_port_t} are {@code int}, {@code CFIndex} is {@code long}, {@code Boolean} is one byte.
   */
  private static final class IOKit {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup IOKIT = SymbolLookup.libraryLookup("/System/Library/Frameworks/IOKit.framework/IOKit", Arena.global());
    private static final SymbolLookup CORE_FOUNDATION =
      SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());

    static final MethodHandle IO_SERVICE_MATCHING = downcall(IOKIT, "IOServiceMatching", FunctionDescriptor.of(ADDRESS, ADDRESS));
    static final MethodHandle IO_SERVICE_GET_MATCHING_SERVICE =
      downcall(IOKIT, "IOServiceGetMatchingService", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));
    static final MethodHandle IO_REGISTRY_ENTRY_CREATE_CF_PROPERTY =
      downcall(IOKIT, "IORegistryEntryCreateCFProperty", FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
    static final MethodHandle IO_OBJECT_RELEASE = downcall(IOKIT, "IOObjectRelease", FunctionDescriptor.of(JAVA_INT, JAVA_INT));

    static final MethodHandle CF_STRING_CREATE_WITH_C_STRING =
      downcall(CORE_FOUNDATION, "CFStringCreateWithCString", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT));
    static final MethodHandle CF_STRING_GET_C_STRING =
      downcall(CORE_FOUNDATION, "CFStringGetCString", FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT));
    static final MethodHandle CF_RELEASE = downcall(CORE_FOUNDATION, "CFRelease", FunctionDescriptor.ofVoid(ADDRESS));

    private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
      return LINKER.downcallHandle(lookup.findOrThrow(name), descriptor);
    }
  }
}
