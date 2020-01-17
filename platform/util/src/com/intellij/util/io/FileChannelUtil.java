// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.nio.ch.FileChannelImpl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.channels.FileChannel;

class FileChannelUtil {
  private static final Logger LOG = Logger.getInstance(FileChannelUtil.class);

  private static final MethodHandle setUnInterruptible = setupUnInterruptibleHandle();

  @Nullable
  private static MethodHandle setupUnInterruptibleHandle() {
    MethodHandle setUnInterruptible = null;
    try {
      //noinspection SpellCheckingInspection,JavaLangInvokeHandleSignature
      setUnInterruptible = MethodHandles
        .lookup()
        .findVirtual(FileChannelImpl.class, "setUninterruptible", MethodType.methodType(void.class));
      LOG.info("un-interruptible FileChannel-s will be used for indexes");
    }
    catch (NoSuchMethodException e) {
      LOG.info("interruptible FileChannel-s will be used for indexes");
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    return setUnInterruptible;
  }

  @NotNull
  static FileChannel unInterruptible(@NotNull FileChannel channel) {
    try {
      if (setUnInterruptible != null && channel instanceof FileChannelImpl) {
        setUnInterruptible.invoke(channel);
      }
    }
    catch (Throwable e) {
      ExceptionUtil.rethrow(e);
    }
    return channel;
  }
}
