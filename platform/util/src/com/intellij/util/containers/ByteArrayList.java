// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.DeprecatedMethodException;
import gnu.trove.TByteArrayList;

/**
 * @deprecated use {@link TByteArrayList instead}
 */
@Deprecated
public class ByteArrayList extends TByteArrayList {
  /**
   * @deprecated use {@link TByteArrayList instead}
   */
  @Deprecated
  public ByteArrayList() {
    DeprecatedMethodException.report("Use gnu.trove.TByteArrayList instead");
  }
}
