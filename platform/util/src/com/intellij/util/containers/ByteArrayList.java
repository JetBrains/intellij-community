// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import gnu.trove.TByteArrayList;

/**
 * @deprecated use {@link TByteArrayList instead}
 */
@Deprecated
public class ByteArrayList extends TByteArrayList {
  public ByteArrayList() {
  }

  public ByteArrayList(int capacity) {
    super(capacity);
  }

  public ByteArrayList(byte[] values) {
    super(values);
  }
}
