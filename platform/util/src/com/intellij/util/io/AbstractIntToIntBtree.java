// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.stats.BTreeStatistics;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 *
 */
public abstract class AbstractIntToIntBtree {

  public static int version() {
    return 4 + (IOUtil.useNativeByteOrderForByteBuffers() ? 0xFF : 0);
  }

  public abstract void persistVars(@NotNull BtreeDataStorage storage,
                                   boolean toDisk) throws IOException;

  public abstract boolean get(int key,
                              int @NotNull [] result) throws IOException;

  public abstract void put(int key,
                           int value) throws IOException;

  public abstract boolean processMappings(@NotNull KeyValueProcessor processor) throws IOException;

  public abstract @NotNull BTreeStatistics getStatistics() throws IOException;

  public abstract void doClose() throws IOException;

  public abstract void doFlush() throws IOException;

  public interface BtreeDataStorage {
    int persistInt(int offset,
                   int value,
                   boolean toDisk) throws IOException;
  }

  public abstract static class KeyValueProcessor {
    /** @return false to stop iterations */
    public abstract boolean process(int key,
                                    int value) throws IOException;
  }

}
