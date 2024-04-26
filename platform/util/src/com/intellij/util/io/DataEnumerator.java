// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface DataEnumerator<Data> {
  /** id=0 used as NULL (i.e. absent) value */
  int NULL_ID = 0;

  int enumerate(@Nullable Data value) throws IOException;

  /** @return value for the id, or null, if such a value not known to this enumerator */
  @Nullable
  Data valueOf(int idx) throws IOException;
}
