// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.ReviseWhenPortedToJDK;
import org.jetbrains.annotations.NotNull;

public class ObjectUtilsRt {
  /**
   * They promised in <a href="http://mail.openjdk.org/pipermail/core-libs-dev/2018-February/051312.html">core-dev mailing list</a> that
   * the object reference won't be removed by JIT and GC-ed until this method execution is finished.
   * <p>
   * In Java 9+ compatible modules use {@link java.lang.ref.Reference#reachabilityFence(Object)} instead.
   */
  @ReviseWhenPortedToJDK("9")
  public static void reachabilityFence(@SuppressWarnings("unused") @NotNull Object o) { }
}
