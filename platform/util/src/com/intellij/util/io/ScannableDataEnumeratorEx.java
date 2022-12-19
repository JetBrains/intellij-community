// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Interface for enumerators that allow scanning through all their entries.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public interface ScannableDataEnumeratorEx<Data> extends DataEnumeratorEx<Data> {

  /**
   * @return true if all available entries were processed, false if scanning was stopped earlier
   * by processor returning false
   */
  @ApiStatus.Internal
  boolean processAllDataObjects(@NotNull Processor<? super Data> processor) throws IOException;
}
