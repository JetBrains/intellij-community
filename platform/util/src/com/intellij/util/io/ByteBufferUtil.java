// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

public final class ByteBufferUtil {
  /**
   * Please use with care. In most cases leaving the job to the GC is enough.
   */
  public static boolean cleanBuffer(@NotNull ByteBuffer buffer) {
    if (!buffer.isDirect()) return true;

    if (SystemInfoRt.IS_AT_LEAST_JAVA9) {
      // in Java 9+, the "official" dispose method is sun.misc.Unsafe#invokeCleaner
      Unsafe unsafe = AtomicFieldUpdater.getUnsafe();
      try {
        MethodType type = MethodType.methodType(void.class, ByteBuffer.class);
        @SuppressWarnings("JavaLangInvokeHandleSignature") MethodHandle handle = MethodHandles.lookup().findVirtual(Unsafe.class, "invokeCleaner", type);
        handle.invokeExact(unsafe, buffer);
        return true;
      }
      catch (Throwable t) {
        Logger.getInstance(ByteBufferUtil.class).warn(t);
        return false;
      }
    }
    return false;
  }
}