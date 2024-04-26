// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Interface for enumerators that allow scanning through all their entries.
 */
@ApiStatus.Internal
public interface ScannableDataEnumeratorEx<Data> extends DataEnumeratorEx<Data> {

  /**
   * @return true if all entries in the enumerator were processed, false if scanning was stopped earlier
   * by processor returning false
   */
  boolean forEach(@NotNull ValueReader<? super Data> reader) throws IOException;

  int recordsCount() throws IOException;

  interface ValueReader<Data> {
    /** @return true if reading should continue, false to stop the reading */
    boolean read(int valueId, Data value) throws IOException;
  }
}
