// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Just to compare 'classic' strict enumerator against non-strict
 */
public class PersistentStringEnumeratorTest extends NonStrictStringsEnumeratorTestBase<PersistentStringEnumerator>{
  @Override
  protected PersistentStringEnumerator openEnumerator(final @NotNull Path storagePath) throws IOException {
    return new PersistentStringEnumerator(storagePath);
  }
}
