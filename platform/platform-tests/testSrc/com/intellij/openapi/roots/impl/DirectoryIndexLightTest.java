// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DirectoryIndexLightTest extends BasePlatformTestCase {

  public void testAccessPerformance() throws IOException {
    VirtualFile fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    List<VirtualFile> files = new ArrayList<>();
    WriteAction.run(() -> {
      for (int i = 0; i < 100; i++) {
        VirtualFile directory = myFixture.getTempDirFixture().findOrCreateDir("dir" + i);
        for (int j = 0; j < 50; j++) {
          directory = directory.createChildDirectory(this, "subDir");
        }
        for (int j = 0; j < 50; j++) {
          files.add(directory.createChildData(this, "file" + j));
        }
      }
    });

    List<LightVirtualFile> filesWithoutId = Arrays.asList(new MyVirtualFile1(), new MyVirtualFile2(), new MyVirtualFile3());

    ProjectFileIndex index = ProjectFileIndex.getInstance(getProject());

    PlatformTestUtil.startPerformanceTest("Directory index query", 2500, () -> {
      for (int i = 0; i < 100; i++) {
        assertFalse(index.isInContent(fsRoot));
        for (LightVirtualFile file : filesWithoutId) {
          assertFalse(file instanceof VirtualFileWithId);
          assertFalse(index.isInContent(file));
          assertFalse(index.isInSource(file));
          assertFalse(index.isInLibrary(file));
        }
        for (VirtualFile file : files) {
          assertTrue(index.isInContent(file));
          assertTrue(index.isInSource(file));
          assertFalse(index.isInLibrary(file));
        }
      }
    }).reattemptUntilJitSettlesDown().assertTiming();
  }

  private static class MyVirtualFile1 extends LightVirtualFile {
  }

  private static class MyVirtualFile2 extends LightVirtualFile {
  }

  private static class MyVirtualFile3 extends LightVirtualFile {
  }

}
