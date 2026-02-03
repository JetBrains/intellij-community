// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.concurrency;

public interface Obsolescent {
  /**
   * @return {@code true} if result of computation won't be used so computation may be interrupted
   */
  boolean isObsolete();
}
