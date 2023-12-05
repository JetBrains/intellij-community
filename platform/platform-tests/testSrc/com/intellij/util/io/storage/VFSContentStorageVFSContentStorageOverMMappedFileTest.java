// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.openapi.vfs.newvfs.persistent.dev.VFSContentStorageOverMMappedFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public class VFSContentStorageVFSContentStorageOverMMappedFileTest extends VFSContentStorageTestBase<VFSContentStorageOverMMappedFile> {
  @Override
  protected @NotNull VFSContentStorageOverMMappedFile openStorage(@NotNull Path storagePath) throws IOException {
    return new VFSContentStorageOverMMappedFile(storagePath, 64);
  }
}
