// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.IOException;

public class PsiModificationTrackerTreeChangesUpdatesTest extends HeavyPlatformTestCase {
  private PsiModificationTrackerImpl myTracker;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // since we want to test PsiModificationTrackerImpl in isolation, we create a separate instance:
    // The existing PsiModificationTrackerImpl is affected by various components.
    myTracker = new PsiModificationTrackerImpl(getProject());
    PsiManagerEx.getInstanceEx(getProject()).addTreeChangePreprocessor(myTracker, getTestRootDisposable());
  }

  @Override
  public void tearDown() throws Exception {
    myTracker = null;
    super.tearDown();
  }

  public void testMoveFile() throws IOException {
    WriteAction.runAndWait(() -> {
      final VirtualFile dir1 = PlatformTestUtil.getOrCreateProjectBaseDir(getProject()).createChildDirectory(this, "dir1");
      final VirtualFile dir2 = PlatformTestUtil.getOrCreateProjectBaseDir(getProject()).createChildDirectory(this, "dir2");
      VirtualFile child = dir1.createChildData(this, "child");

      long outOfCodeBlockCount = myTracker.getModificationCount();
      child.move(this, dir2);
      assertFalse(myTracker.getModificationCount() == outOfCodeBlockCount);

      outOfCodeBlockCount = myTracker.getModificationCount();
      child.move(this, dir1);
      assertFalse(myTracker.getModificationCount() == outOfCodeBlockCount);
    });
  }

  public void testMoveDir() throws IOException {
    WriteAction.runAndWait(() -> {
      final VirtualFile dir1 = PlatformTestUtil.getOrCreateProjectBaseDir(getProject()).createChildDirectory(this, "dir1");
      final VirtualFile dir2 = PlatformTestUtil.getOrCreateProjectBaseDir(getProject()).createChildDirectory(this, "dir2");
      VirtualFile child = dir1.createChildDirectory(this, "child");

      long outOfCodeBlockCount = myTracker.getModificationCount();
      child.move(this, dir2);
      assertFalse(myTracker.getModificationCount() == outOfCodeBlockCount);

      outOfCodeBlockCount = myTracker.getModificationCount();
      child.move(this, dir1);
      assertFalse(myTracker.getModificationCount() == outOfCodeBlockCount);
    });
  }
}
