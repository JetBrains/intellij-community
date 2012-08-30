/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.util.ThrowableRunnable;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FileUtilPerformanceTest {
  @Test
  public void toCanonicalPath() throws Exception {
    final String testPath = "/a/b/c/./d///e/../f/g/h/i/j/";
    assertEquals("/a/b/c/d/f/g/h/i/j", FileUtil.toCanonicalPath(testPath));

    PlatformTestUtil.startPerformanceTest("", 1000, new ThrowableRunnable() {
      @Override
      public void run() throws Throwable {
        for (int i = 0; i < 1000000; ++i) {
          final String canonicalPath = FileUtil.toCanonicalPath(testPath, '/');
          assert canonicalPath != null && canonicalPath.length() == 18 : canonicalPath;
        }
      }
    }).cpuBound().assertTiming();
  }
}
