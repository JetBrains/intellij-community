// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger;

/**
 * @author nik
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface Obsolescent extends org.jetbrains.concurrency.Obsolescent {

  /**
   * @return {@code true} if result of computation won't be used so computation may be interrupted
   */
  default boolean isObsolete() {
    return false;
  }
}
