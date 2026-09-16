package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.nio.charset.StandardCharsets.UTF_8;

@ApiStatus.Internal
public final class MacIoRegistry {
  private MacIoRegistry() { }

  public static @NotNull Map<String, String> platformExpertData(@NotNull List<String> properties) {
    try (var arena = Arena.ofConfined()) {
      var matching = (MemorySegment)Handles.SERVICE_MATCHING.invokeExact(arena.allocateFrom("IOPlatformExpertDevice"));
      if (matching.address() == 0) return Map.of();
      var service = (int)Handles.MATCHING_SERVICE.invokeExact(0, matching);
      if (service == 0) return Map.of();
      try {
        var values = new LinkedHashMap<String, String>();
        for (var property : properties) {
          var key =
            (MemorySegment)Handles.CREATE_STRING.invokeExact(MemorySegment.NULL, arena.allocateFrom(property), 0x08000100);
          if (key.address() == 0) throw new OutOfMemoryError("CFStringCreateWithCString failed");
          try {
            var value = (MemorySegment)Handles.CREATE_PROPERTY.invokeExact(service, key, MemorySegment.NULL, 0);
            if (value.address() != 0) {
              try {
                if ((long)Handles.TYPE_ID.invokeExact(value) == (long)Handles.DATA_TYPE_ID.invokeExact()) {
                  var size = (long)Handles.DATA_LENGTH.invokeExact(value);
                  var bytes = (MemorySegment)Handles.DATA_BYTES.invokeExact(value);
                  var data = size == 0 ? new byte[0] : bytes.reinterpret(size).toArray(JAVA_BYTE);
                  var end = 0;
                  while (end < data.length && data[end] != 0) {
                    end++;
                  }
                  values.put(property, new String(data, 0, end, UTF_8));
                }
              }
              finally {
                Handles.RELEASE.invokeExact(value);
              }
            }
          }
          finally {
            Handles.RELEASE.invokeExact(key);
          }
        }
        return values;
      }
      finally {
        var _ = (int)Handles.RELEASE_SERVICE.invokeExact(service);
      }
    }
    catch (RuntimeException | Error failure) {
      throw failure;
    }
    catch (Throwable failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup IO_KIT =
      SymbolLookup.libraryLookup("/System/Library/Frameworks/IOKit.framework/IOKit", Arena.global());
    private static final SymbolLookup CORE_FOUNDATION =
      SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());
    static final MethodHandle SERVICE_MATCHING =
      LINKER.downcallHandle(IO_KIT.findOrThrow("IOServiceMatching"), FunctionDescriptor.of(ADDRESS, ADDRESS));
    static final MethodHandle MATCHING_SERVICE =
      LINKER.downcallHandle(IO_KIT.findOrThrow("IOServiceGetMatchingService"), FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));
    static final MethodHandle CREATE_PROPERTY = LINKER.downcallHandle(
      IO_KIT.findOrThrow("IORegistryEntryCreateCFProperty"), FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
    static final MethodHandle RELEASE_SERVICE =
      LINKER.downcallHandle(IO_KIT.findOrThrow("IOObjectRelease"), FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    static final MethodHandle CREATE_STRING = LINKER.downcallHandle(
      CORE_FOUNDATION.findOrThrow("CFStringCreateWithCString"), FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT));
    static final MethodHandle TYPE_ID =
      LINKER.downcallHandle(CORE_FOUNDATION.findOrThrow("CFGetTypeID"), FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    static final MethodHandle DATA_TYPE_ID =
      LINKER.downcallHandle(CORE_FOUNDATION.findOrThrow("CFDataGetTypeID"), FunctionDescriptor.of(JAVA_LONG));
    static final MethodHandle DATA_LENGTH =
      LINKER.downcallHandle(CORE_FOUNDATION.findOrThrow("CFDataGetLength"), FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    static final MethodHandle DATA_BYTES =
      LINKER.downcallHandle(CORE_FOUNDATION.findOrThrow("CFDataGetBytePtr"), FunctionDescriptor.of(ADDRESS, ADDRESS));
    static final MethodHandle RELEASE = LINKER.downcallHandle(CORE_FOUNDATION.findOrThrow("CFRelease"), FunctionDescriptor.ofVoid(ADDRESS));
  }
}
