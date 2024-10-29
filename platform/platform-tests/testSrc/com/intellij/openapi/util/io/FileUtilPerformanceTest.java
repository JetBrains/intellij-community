// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileUtilPerformanceTest {
  private static final String myTestPath = "/a/b/c/./d///e/../f/g/h/i/j/";
  private static final String myCanonicalPath = "/a/b/c/d/f/g/h/i/j";
  private static final String mySimpleTestPath = "file.txt";

  @Test
  public void toCanonicalPath() {
    assertEquals(myCanonicalPath, FileUtil.toCanonicalPath(myTestPath));

    Benchmark.newBenchmark("toCanonicalPath", () -> {
      for (int i = 0; i < 1000000; ++i) {
        final String canonicalPath = FileUtil.toCanonicalPath(myTestPath, '/');
        assert canonicalPath.length() == 18 : canonicalPath;
      }
    }).start();
  }

  @Test
  public void toCanonicalPathSimple() {
    assertEquals(mySimpleTestPath, FileUtil.toCanonicalPath(mySimpleTestPath));

    Benchmark.newBenchmark("toCanonicalPathSimple", () -> {
      for (int i = 0; i < 1000000; ++i) {
        final String canonicalPath = FileUtil.toCanonicalPath(mySimpleTestPath, '/');
        assert canonicalPath.length() == 8 : canonicalPath;
      }
    }).start();
  }

  @Test
  public void isAncestor() {
    assertTrue(FileUtil.isAncestor(myTestPath, myCanonicalPath, false));

    Benchmark.newBenchmark("isAncestor", () -> {
      for (int i = 0; i < 1000000; ++i) {
        assert FileUtil.isAncestor(myTestPath, myCanonicalPath, false);
        assert !FileUtil.isAncestor(myTestPath, myCanonicalPath, true);
      }
    }).start();
  }
}
