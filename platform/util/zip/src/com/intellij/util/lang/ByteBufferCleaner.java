// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

// we need to unmap buffer immediately without waiting until GC does this job; otherwise further modifications of the created file
// will fail with AccessDeniedException
@Internal
public final class ByteBufferCleaner {
  private static final MethodHandle cleanerHandle;
  private static final MethodHandle cleanerCleanHandle;

  public static void unmapBuffer(@NotNull ByteBuffer buffer) throws Exception {
    if (!buffer.isDirect()) {
      return;
    }
    try {
      //noinspection JavaLangInvokeHandleSignature
      Object cleaner = cleanerHandle.invoke(buffer);
      if (cleaner != null) {
        cleanerCleanHandle.invoke(cleaner);
      }
    }
    catch (Error | Exception e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  static  {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      Class<?> directBufferClass = Class.forName("sun.nio.ch.DirectBuffer");
      Class<?> cleanerClass = directBufferClass.getDeclaredMethod("cleaner").getReturnType();
      cleanerHandle = lookup.findVirtual(directBufferClass, "cleaner", MethodType.methodType(cleanerClass));
      cleanerCleanHandle = lookup.findVirtual(cleanerClass, "clean", MethodType.methodType(Void.TYPE));
    }
    catch (Error | RuntimeException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new IllegalStateException(e);
    }
  }
}
