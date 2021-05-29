// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.ReviseWhenPortedToJDK;
import org.jetbrains.annotations.NotNull;

public class ObjectUtilsRt {
  /**
   * They promise in http://mail.openjdk.java.net/pipermail/core-libs-dev/2018-February/051312.html that
   * the object reference won't be removed by JIT and GC-ed until this call.
   * <p>
   * In Java 11 compatible modules use {@link java.lang.ref.Reference#reachabilityFence(Object)} instead.
   */
  @ReviseWhenPortedToJDK("9")
  public static void reachabilityFence(@SuppressWarnings("unused") @NotNull Object o) { }
}
