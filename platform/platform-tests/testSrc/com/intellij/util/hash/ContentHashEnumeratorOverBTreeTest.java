// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.hash;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

public class ContentHashEnumeratorOverBTreeTest extends ContentHashEnumeratorTestBase {
  @TempDir
  private Path tempDir;

  @Override
  protected ContentHashEnumerator openEnumerator() throws IOException {
    return new ContentHashEnumeratorOverBTree(tempDir.resolve("enumerator_btree"));
  }
}
