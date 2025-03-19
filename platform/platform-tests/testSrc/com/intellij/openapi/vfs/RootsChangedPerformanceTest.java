// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;

public class RootsChangedPerformanceTest extends HeavyPlatformTestCase {
  public void testRootsChangedPerformanceInPresenceOfManyVirtualFilePointers() {
    VirtualFile temp = getTempDir().createVirtualDir();
    String dirName = "xxx";
    VirtualFilePointerManager virtualFilePointerManager = VirtualFilePointerManager.getInstance();
    for (int i = 0; i < 10_000; i++) {
      virtualFilePointerManager.create(temp.getUrl() + "/" + dirName + "/" + i, getTestRootDisposable(), null);
    }

    VirtualFile xxx = createChildDirectory(temp, dirName);

    Benchmark.newBenchmark("time wasted in ProjectRootManagerComponent.before/afterValidityChanged()", ()->{
      for (int i = 0; i < 100; i++) {
        rename(xxx, "yyy");
        rename(xxx, dirName);
      }
    }).start();
  }

  @Override
  protected boolean isCreateDirectoryBasedProject() {
    return true;
  }
}
