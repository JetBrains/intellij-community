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
package com.intellij.openapi.util.io;

import com.intellij.testFramework.PlatformTestUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileUtilPerformanceTest {
  private String myTestPath = "/a/b/c/./d///e/../f/g/h/i/j/";
  private String myCanonicalPath = "/a/b/c/d/f/g/h/i/j";
  private String mySimpleTestPath = "file.txt";

  @Test
  public void toCanonicalPath() {
    assertEquals(myCanonicalPath, FileUtil.toCanonicalPath(myTestPath));

    PlatformTestUtil.startPerformanceTest("toCanonicalPath", 650, () -> {
      for (int i = 0; i < 1000000; ++i) {
        final String canonicalPath = FileUtil.toCanonicalPath(myTestPath, '/');
        assert canonicalPath != null && canonicalPath.length() == 18 : canonicalPath;
      }
    }).assertTiming();
  }

  @Test
  public void toCanonicalPathSimple() {
    assertEquals(mySimpleTestPath, FileUtil.toCanonicalPath(mySimpleTestPath));

    PlatformTestUtil.startPerformanceTest("toCanonicalPathSimple", 210, () -> {
      for (int i = 0; i < 1000000; ++i) {
        final String canonicalPath = FileUtil.toCanonicalPath(mySimpleTestPath, '/');
        assert canonicalPath != null && canonicalPath.length() == 8 : canonicalPath;
      }
    }).assertTiming();
  }

  @Test
  public void isAncestor() {
    assertTrue(FileUtil.isAncestor(myTestPath, myCanonicalPath, false));

    PlatformTestUtil.startPerformanceTest("isAncestor", 3000, () -> {
      for (int i = 0; i < 1000000; ++i) {
        assert FileUtil.isAncestor(myTestPath, myCanonicalPath, false);
        assert !FileUtil.isAncestor(myTestPath, myCanonicalPath, true);
      }
    }).assertTiming();
  }
}
