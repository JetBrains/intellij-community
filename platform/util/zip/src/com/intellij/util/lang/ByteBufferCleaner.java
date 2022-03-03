// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

// we need to unmap buffer immediately without waiting until GC does this job; otherwise further modifications of the created file
// will fail with AccessDeniedException
public final class ByteBufferCleaner {
  private static volatile MethodHandle cleaner;

  public static void unmapBuffer(@NotNull ByteBuffer buffer) throws Exception {
    if (!buffer.isDirect()) {
      return;
    }

    MethodHandle cleaner = ByteBufferCleaner.cleaner;
    try {
      if (cleaner == null) {
        cleaner = getByteBufferCleaner();
      }
      cleaner.invokeExact(buffer);
    }
    catch (Exception e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private synchronized static @NotNull MethodHandle getByteBufferCleaner() throws Throwable {
    MethodHandle cleaner = ByteBufferCleaner.cleaner;
    if (cleaner != null) {
      return cleaner;
    }

    Class<?> unsafeClass = ClassLoader.getPlatformClassLoader().loadClass("sun.misc.Unsafe");
    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(unsafeClass, MethodHandles.lookup());
    Object unsafe = lookup.findStaticGetter(unsafeClass, "theUnsafe", unsafeClass).invoke();
    cleaner = lookup.findVirtual(unsafeClass, "invokeCleaner", MethodType.methodType(Void.TYPE, ByteBuffer.class)).bindTo(unsafe);
    ByteBufferCleaner.cleaner = cleaner;
    return cleaner;
  }
}
