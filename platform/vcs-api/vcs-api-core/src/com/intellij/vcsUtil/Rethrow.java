// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import com.intellij.util.ExceptionUtil;

/**
 * @author irengrig
 *
 * @deprecated use {@link ExceptionUtil} instead
 */
@Deprecated
public final class Rethrow {
  private Rethrow() {
  }

  /**
   * @deprecated use {@link ExceptionUtil#rethrowAllAsUnchecked(Throwable)} instead
   */
  @Deprecated
  public static void reThrowRuntime(final Throwable t) {
    ExceptionUtil.rethrowAllAsUnchecked(t);
  }
}
