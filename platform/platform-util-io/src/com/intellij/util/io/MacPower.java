// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
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

/**
 * The power source that supplies the machine, from an {@code IOPSGetProvidingPowerSourceType} downcall into IOKit.
 * macOS only: the first call loads IOKit and CoreFoundation.
 */
@ApiStatus.Internal
public final class MacPower {
  private MacPower() { }

  /** {@code kIOPMACPowerKey} */
  public static final String AC_POWER = "AC Power";
  /** {@code kIOPMBatteryPowerKey} */
  public static final String BATTERY_POWER = "Battery Power";
  /** {@code kIOPMUPSPowerKey} */
  public static final String UPS_POWER = "UPS Power";

  private static final int KCF_STRING_ENCODING_UTF8 = 0x08000100;
  private static final long BUFFER_SIZE = 64;

  /**
   * @return {@link #AC_POWER}, {@link #BATTERY_POWER} or {@link #UPS_POWER}, or {@code null} when IOKit has no power source information
   */
  public static @Nullable String providingPowerSourceType() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment blob = (MemorySegment)Handles.IOPS_COPY_POWER_SOURCES_INFO.invokeExact();
      if (blob.address() == 0) {
        return null;
      }
      try {
        // the string is owned by the framework; do not release it
        MemorySegment type = (MemorySegment)Handles.IOPS_GET_PROVIDING_POWER_SOURCE_TYPE.invokeExact(blob);
        if (type.address() == 0) {
          return null;
        }
        MemorySegment buffer = arena.allocate(BUFFER_SIZE);
        byte copied = (byte)Handles.CF_STRING_GET_C_STRING.invokeExact(type, buffer, BUFFER_SIZE, KCF_STRING_ENCODING_UTF8);
        return copied != 0 ? buffer.getString(0) : null;
      }
      finally {
        Handles.CF_RELEASE.invokeExact(blob);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** Both frameworks live in the dyld shared cache, so the {@code String} overload of {@code libraryLookup} goes through {@code dlopen}. */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup IOKIT = SymbolLookup.libraryLookup("/System/Library/Frameworks/IOKit.framework/IOKit", Arena.global());
    private static final SymbolLookup CORE_FOUNDATION =
      SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());

    /** {@code CFTypeRef IOPSCopyPowerSourcesInfo(void)}, a retained blob */
    static final MethodHandle IOPS_COPY_POWER_SOURCES_INFO = LINKER.downcallHandle(IOKIT.findOrThrow("IOPSCopyPowerSourcesInfo"), FunctionDescriptor.of(ADDRESS));
    /** {@code CFStringRef IOPSGetProvidingPowerSourceType(CFTypeRef snapshot)}, not retained */
    static final MethodHandle IOPS_GET_PROVIDING_POWER_SOURCE_TYPE =
      LINKER.downcallHandle(IOKIT.findOrThrow("IOPSGetProvidingPowerSourceType"), FunctionDescriptor.of(ADDRESS, ADDRESS));
    /** {@code Boolean CFStringGetCString(CFStringRef, char *buffer, CFIndex size, CFStringEncoding)} */
    static final MethodHandle CF_STRING_GET_C_STRING =
      LINKER.downcallHandle(CORE_FOUNDATION.findOrThrow("CFStringGetCString"), FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT));
    /** {@code void CFRelease(CFTypeRef)} */
    static final MethodHandle CF_RELEASE = LINKER.downcallHandle(CORE_FOUNDATION.findOrThrow("CFRelease"), FunctionDescriptor.ofVoid(ADDRESS));
  }
}
