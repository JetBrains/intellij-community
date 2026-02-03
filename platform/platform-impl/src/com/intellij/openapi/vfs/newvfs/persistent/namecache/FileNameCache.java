// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.namecache;

import com.intellij.util.io.DataEnumerator;
import com.intellij.util.io.DataEnumeratorEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Actually the only difference from {@link DataEnumerator<String>} is the nullability.
 * MAYBE RC: Probably just drop the interface in favor of pure {@link DataEnumerator<String>}?
 */
@ApiStatus.Internal
public interface FileNameCache extends DataEnumeratorEx<@NotNull String>, AutoCloseable {
  @Override
  int tryEnumerate(@NotNull String value) throws IOException;

  @Override
  int enumerate(@NotNull String name) throws IOException;

  @Override
  @NotNull String valueOf(int nameId) throws IOException;

  @Override
  void close() throws Exception;
}
