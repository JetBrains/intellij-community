/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.FilePathSplittingPolicy;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * author: lesya
 */
public class FileNameSplittingTest extends TestCase {
  private static final File FILE = new File("c:/dir1/dir2/dir3/dir4/file.txt");

  private FilePathSplittingPolicy myPolicy;

  public void testSplitByLetter() {
    myPolicy = FilePathSplittingPolicy.SPLIT_BY_LETTER;
    @NonNls String[] expectedStrings = {
      "t",
      "xt",
      "txt",
      ".txt",
      "e.txt",
      "le.txt",
      "ile.txt",
      "file.txt",
      ".file.txt",
      "..file.txt",
      "...file.txt",
      "c...file.txt",
      "c.../file.txt",
      "c:.../file.txt",
      "c:...4/file.txt",
      "c:/...4/file.txt",
      "c:/...r4/file.txt",
      "c:/d...r4/file.txt",
      "c:/d...ir4/file.txt",
      "c:/di...ir4/file.txt",
      "c:/di...dir4/file.txt",
      "c:/dir...dir4/file.txt",
      "c:/dir.../dir4/file.txt",
      "c:/dir1.../dir4/file.txt",
      "c:/dir1/...3/dir4/file.txt",
      "c:/dir1/...r3/dir4/file.txt",
      "c:/dir1/d...r3/dir4/file.txt",
      "c:/dir1/d...ir3/dir4/file.txt",
      "c:/dir1/di...ir3/dir4/file.txt",
      "c:/dir1/dir2/dir3/dir4/file.txt"
    };

    for (String expectedString : expectedStrings) {
      doTest(expectedString, expectedString.length());
    }

  }

  public void testSplitByFileSeparator(){
    myPolicy = FilePathSplittingPolicy.SPLIT_BY_SEPARATOR;
    @NonNls String[] expectedStrings = {
      "...",
      "...",
      "...",
      "...",
      "...",
      "...",
      "...",
      "file.txt",
      "file.txt",
      "file.txt",
      "...file.txt",
      "...file.txt",
      "c:...file.txt",
      "c:.../file.txt",
      "c:/.../file.txt",
      "c:/.../file.txt",
      "c:/.../file.txt",
      "c:/.../file.txt",
      "c:/...dir4/file.txt",
      "c:/...dir4/file.txt",
      "c:/...dir4/file.txt",
      "c:/...dir4/file.txt",
      "c:/dir1...dir4/file.txt",
      "c:/dir1.../dir4/file.txt",
      "c:/dir1/.../dir4/file.txt",
      "c:/dir1/.../dir4/file.txt",
      "c:/dir1/.../dir4/file.txt",
      "c:/dir1/.../dir4/file.txt",
      "c:/dir1/...dir3/dir4/file.txt",
      "c:/dir1/...dir3/dir4/file.txt",
      "c:/dir1/dir2/dir3/dir4/file.txt"
    };
    for (int i = 0; i < expectedStrings.length; i++) {
      int count = i + 1;
      String expectedString = expectedStrings[i];
      assertTrue(expectedString,  expectedString.length() <= count || i < 3);
      doTest(expectedString, count);
    }

  }

  public void testPerformance() {
    myPolicy = FilePathSplittingPolicy.SPLIT_BY_SEPARATOR;

    PlatformTestUtil.startPerformanceTest("FileNameSplitting", 70, () -> {
      for (int i = 0; i < 100; i++) {
        for (int j = 0; j < FILE.getPath().length(); j++)
          myPolicy.getPresentableName(FILE, j);
      }
    }).assertTiming();
  }

  private void doTest(String expected, int count) {
    assertEquals(expected.replace('/', File.separatorChar),
                 myPolicy.getPresentableName(FILE, count));
  }
}
