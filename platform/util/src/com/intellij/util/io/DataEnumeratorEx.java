// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface DataEnumeratorEx<Data> extends DataEnumerator<Data> {

  /**
   * @return id of the value, if value is already known to the enumerator,
   * or NULL_ID, if value is not known yet
   */
  int tryEnumerate(@Nullable Data value) throws IOException;
}
