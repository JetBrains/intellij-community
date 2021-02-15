// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

public final class ByteBufferUtil {
  /**
   * Please use with care. In most cases leaving the job to the GC is enough.
   */
  @ReviseWhenPortedToJDK("11")
  public static boolean cleanBuffer(@NotNull ByteBuffer buffer) {
    if (!buffer.isDirect()) return true;

    if (JavaVersion.current().feature >= 9) {
      // in Java 9+, the "official" dispose method is sun.misc.Unsafe#invokeCleaner
      Object unsafe = ReflectionUtil.getUnsafe();
      try {
        MethodType type = MethodType.methodType(void.class, ByteBuffer.class);
        MethodHandle handle = MethodHandles.lookup().findVirtual(unsafe.getClass(), "invokeCleaner", type);
        handle.invoke(unsafe, buffer);
        return true;
      }
      catch (Throwable t) {
        Logger.getInstance(ByteBufferUtil.class).warn(t);
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
        Logger.getInstance(ByteBufferUtil.class).warn(e);
        return false;
      }
    }
  }
}
