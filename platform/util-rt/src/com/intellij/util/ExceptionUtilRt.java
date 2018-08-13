// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class ExceptionUtilRt {
  public static void rethrowUnchecked(@Nullable Throwable t) {
    if (t instanceof Error) throw (Error)t;
    if (t instanceof RuntimeException) throw (RuntimeException)t;
  }

  @Contract("!null->fail")
  public static void rethrowAll(@Nullable Throwable t) throws Exception {
    if (t != null) {
      rethrowUnchecked(t);
      throw (Exception)t;
    }
  }

  @Contract("_->fail")
  public static void rethrow(@Nullable Throwable throwable) {
    rethrowUnchecked(throwable);
    throw new RuntimeException(throwable);
  }

  @Contract("!null->fail")
  public static void rethrowAllAsUnchecked(@Nullable Throwable t) {
    if (t != null) {
      rethrow(t);
    }
  }

}
