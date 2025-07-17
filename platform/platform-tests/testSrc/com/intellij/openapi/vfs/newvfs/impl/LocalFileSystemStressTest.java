// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LocalFileSystemStressTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void getPathForVeryDeepFileMustNotFailWithStackOverflowError_Performance() throws IOException {
    VirtualFile tmpRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    assertNotNull(tmpRoot);
    assertThat(tmpRoot.getFileSystem()).isInstanceOf(TempFileSystem.class);
    VirtualFile testDir = WriteAction.computeAndWait(() -> tmpRoot.createChildDirectory(this, getTestName(true)));
    int N_LEVELS = 1_000_000;
    Benchmark.newBenchmark(getTestName(false), () -> {
      UIUtil.pump();
      StringBuilder expectedPath = new StringBuilder(N_LEVELS*4+100);
      expectedPath.append(testDir.getPath());
      VirtualFile nested = WriteAction.computeAndWait(() -> {
        VirtualFile v = testDir;
        VfsData.Segment segment = new VfsData.Segment(new VfsData(ApplicationManager.getApplication(), (PersistentFSImpl)PersistentFS.getInstance()));
        VfsData.DirectoryData directoryData = new VfsData.DirectoryData();
        for (int i = 1; i < N_LEVELS; i++) {
          // create VirtualDirectory manually instead of calling "createChildDirectory" to avoid filling persistence with garbage, which is slow and harmful for other tests
          v = new VirtualDirectoryImpl(i, segment, directoryData, (VirtualDirectoryImpl)v, TempFileSystem.getInstance()){
            @Override
            public @NotNull CharSequence getNameSequence() {
              return "dir";
            }

            @Override
            public @NotNull String getName() {
              return "dir";
            }
          };
          expectedPath.append("/").append(v.getName());
        }
        return v;
      });

      try {
        assertEquals(expectedPath.toString(), nested.getPath());
      }
      finally {
        WriteAction.runAndWait(() -> testDir.delete(this));
      }
    }).attempts(1).start();
  }
}