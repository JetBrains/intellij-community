// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.Function;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FileOffsetsManagerTest extends BasePlatformTestCase {

  private static class TestInfo {
    private final FileOffsetsManager manager;
    private final VirtualFile file;
    private final int offset;

    private TestInfo(FileOffsetsManager manager, VirtualFile file, int offset) {
      this.manager = manager;
      this.file = file;
      this.offset = offset;
    }
  }

  private void doTest(String fileContent, Function<TestInfo, Integer> converter, int[] offsets, int[] expectedResult) throws IOException {
    assertEquals(offsets.length, expectedResult.length);

    VirtualFile file = WriteAction.compute(() -> {
      VirtualFile root = ModuleRootManager.getInstance(getModule()).getContentRoots()[0];
      VirtualFile f = root.createChildData(this, "foo.txt");
      f.setBinaryContent(fileContent.getBytes(StandardCharsets.UTF_8));
      return f;
    });

    FileOffsetsManager manager = FileOffsetsManager.getInstance();

    for (int i = 0; i < offsets.length; i++) {
      int offset = offsets[i];
      int result = converter.fun(new TestInfo(manager, file, offset));
      assertEquals("index = " + i, expectedResult[i], result);
    }
  }

  public void testOriginalToConvertedOffsets() throws IOException {
    doTest("0\n1\n\n4\r555\r\n666\r\n\r\n8",
           ti -> ti.manager.getConvertedOffset(ti.file, ti.offset),
           new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22},
           new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 11, 12, 13, 14, 15, 15, 16, 16, 17, 18, 19});
  }

  public void testConvertedToOriginalOffsets() throws IOException {
    doTest("0\n1\n\n4\r555\r\n666\r\n\r\n88",
           ti -> ti.manager.getOriginalOffset(ti.file, ti.offset),
           new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18},
           new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14, 15, 17, 19, 20, 21});
  }
}
