// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static com.intellij.openapi.vfs.newvfs.persistent.FSRecords.MIN_REGULAR_FILE_ID;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class VirtualFile_IterativeVsRecursiveComputePath_Test {

  private static final int MAX_FILES_TO_TEST = 1_000_000;

  @Test
  void iterativeAndRecursivePathsComputation_MustProduceSameResults() {
    forManyVirtualFilesFromVFS(
      PersistentFS.getInstance(),
      file -> {
        String iterativePath = VirtualFileSystemEntry.computePathIteratively(file, "", "");
        String recursivePath = VirtualFileSystemEntry.computePathRecursively(file, "", "").toString();
        assertThat(iterativePath)
          .describedAs("file(" + file + ")")
          .isEqualTo(recursivePath);
      });
  }

  @Test
  void iterativeAndRecursiveUrlsComputation_MustProduceSameResults() {
    String protoSeparator = "://";
    forManyVirtualFilesFromVFS(
      PersistentFS.getInstance(),
      file -> {
        String protocol = file.getFileSystem().getProtocol();

        String iterativeUrl = VirtualFileSystemEntry.computePathIteratively(file, protocol, protoSeparator);
        String recursiveUrl = VirtualFileSystemEntry.computePathRecursively(file, protocol, protoSeparator).toString();
        assertThat(iterativeUrl)
          .describedAs("file(" + file + ")")
          .isEqualTo(recursiveUrl);
      });
  }


  private static void forManyVirtualFilesFromVFS(@NotNull PersistentFS pFS,
                                                 @NotNull Consumer<VirtualFileSystemEntry> consumer) {
    int maxFileId = ((PersistentFSImpl)pFS).peer().connection().records().maxAllocatedID();
    if (maxFileId <= MIN_REGULAR_FILE_ID) {
      return;//nothing to iterate
    }
    int step = 104729;//big prime number
    int totalFilesToTest = Math.min(MAX_FILES_TO_TEST, maxFileId);
    for (int i = 0; i < totalFilesToTest; i++) {
      int fileId = MIN_REGULAR_FILE_ID + Math.abs(i * step) % (maxFileId - MIN_REGULAR_FILE_ID);
      VirtualFileSystemEntry file = (VirtualFileSystemEntry)pFS.findFileById(fileId);
      if (file == null) continue;

      consumer.accept(file);
    }
  }
}
