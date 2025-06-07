// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.openapi.vfs.newvfs.persistent.mapped.content.CompressingAlgo;
import com.intellij.openapi.vfs.newvfs.persistent.mapped.content.VFSContentStorageOverMMappedFile;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public class VFSContentStorageVFSContentStorageOverMMappedFileTest extends VFSContentStorageTestBase<VFSContentStorageOverMMappedFile> {

  //TODO RC: parameterize test for all 3 variants
  protected final CompressingAlgo compressingAlgo = new CompressingAlgo.Lz4Algo( /*compressLargerThan: */ 64 );

  @Override
  protected @NotNull VFSContentStorageOverMMappedFile openStorage(@NotNull Path storagePath) throws IOException {
    return new VFSContentStorageOverMMappedFile(
      storagePath,
      /*pageSize: */ 64 * IOUtil.MiB,
      compressingAlgo
    );
  }
}
