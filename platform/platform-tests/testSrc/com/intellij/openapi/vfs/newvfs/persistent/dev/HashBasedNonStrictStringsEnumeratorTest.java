// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.ResizeableMappedFile;

import java.io.IOException;
import java.nio.file.Path;

/**
 */
public class HashBasedNonStrictStringsEnumeratorTest extends NonStrictStringsEnumeratorTestBase {
  @Override
  protected HashBasedNonStrictStringsEnumerator openEnumerator(final Path storagePath) throws IOException {
    final ResizeableMappedFile mappedFile = new ResizeableMappedFile(
      storagePath,
      10 * PagedFileStorage.MB,
      null,
      -1 /*use default*/,
      false
    );
    return new HashBasedNonStrictStringsEnumerator(mappedFile);
  }
}
