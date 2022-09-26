// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.Unsafe;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

public final class ByteBufferUtil {
  private static final MethodHandle invokeCleaner = findInvokeCleaner();
  private static final MethodHandle address = findAddress();
  private static final int byteArrayBaseOffset = byteArrayBaseOffset();

  @Nullable
  private static MethodHandle findInvokeCleaner() {
    MethodHandle invokeCleaner = null;
    try {
      if (JavaVersion.current().feature >= 9) {
        Object unsafe = ReflectionUtil.getUnsafe();
        MethodType type = MethodType.methodType(void.class, ByteBuffer.class);
        invokeCleaner = MethodHandles.publicLookup().findVirtual(unsafe.getClass(), "invokeCleaner", type).bindTo(unsafe);
      }
    }
    catch (Throwable t) {
      Logger.getInstance(ByteBufferUtil.class).warn(t);
    }
    return invokeCleaner;
  }

  @Nullable
  private static MethodHandle findAddress() {
    MethodHandle address = null;
    try {
      if (JavaVersion.current().feature >= 9) {
        Class<?> directBufferClass = Class.forName("sun.nio.ch.DirectBuffer");
        address = MethodHandles.lookup().findVirtual(directBufferClass,
                                                     "address",
                                                     MethodType.methodType(long.class));
      }
    }
    catch (Throwable ignored) {
    }
    return address;
  }

  private static int byteArrayBaseOffset() {
    return Unsafe.arrayBaseOffset(byte[].class);
  }

  /**
   * Please use with care. In most cases leaving the job to the GC is enough.
   */
  @ReviseWhenPortedToJDK("11")
  public static boolean cleanBuffer(@NotNull ByteBuffer buffer) {
    if (!buffer.isDirect()) return true;

    if (invokeCleaner != null) {
      try {
        invokeCleaner.invoke(buffer);
        return true;
      }
      catch (Throwable t) {
        getLogger().warn(t);
        return false;
      }
    }
    else {
      //used in Kotlin and JPS
      try {
        Class<?> directBufferClass = Class.forName("sun.nio.ch.DirectBuffer");
        Class<?> cleanerClass = Class.forName("sun.misc.Cleaner");
        Object cleaner = directBufferClass.getDeclaredMethod("cleaner").invoke(buffer);
        if (cleaner != null) {
          cleanerClass.getDeclaredMethod("clean").invoke(cleaner);  // already cleaned otherwise
        }
        return true;
      }
      catch (Exception e) {
        getLogger().warn(e);
        return false;
      }
    }
  }

  public static void copyMemory(@NotNull ByteBuffer src, int index, byte[] dst, int dstIndex, int length) {
    if (address != null) {
      try {
        long address = (long)ByteBufferUtil.address.invoke(src);
        Unsafe.copyMemory(null, address + index, dst, byteArrayBaseOffset + dstIndex, length);
        return;
      }
      catch (Throwable e) {
        getLogger().warn(e);
      }
    }

    ByteBuffer buf = src.duplicate();
    buf.position(index);
    buf.get(dst, dstIndex, length);
  }

  public static long getAddress(@NotNull ByteBuffer src) {
    try {
      return (long)address.invoke(src);
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(ByteBufferUtil.class);
  }
}
