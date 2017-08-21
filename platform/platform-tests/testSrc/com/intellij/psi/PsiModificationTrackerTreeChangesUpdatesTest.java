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
package com.intellij.psi;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

public class PsiModificationTrackerTreeChangesUpdatesTest extends PlatformTestCase {
  private PsiModificationTrackerImpl myTracker;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // since we want to test PsiModificationTrackerImpl in isolation, we create a separate instance:
    // The existing PsiModificationTrackerImpl is affected by various components.
    myTracker = new PsiModificationTrackerImpl(getProject());
    ((PsiManagerImpl)PsiManager.getInstance(getProject())).addTreeChangePreprocessor(myTracker);
  }

  @Override
  public void tearDown() throws Exception {
    ((PsiManagerImpl)PsiManager.getInstance(getProject())).removeTreeChangePreprocessor(myTracker);
    myTracker = null;
    super.tearDown();
  }

  public void testMoveFile() {
    new WriteAction<Object>() {
      @Override
      protected void run(@NotNull Result<Object> result) throws Throwable {
        final VirtualFile dir1 = getProject().getBaseDir().createChildDirectory(this, "dir1");
        final VirtualFile dir2 = getProject().getBaseDir().createChildDirectory(this, "dir2");
        VirtualFile child = dir1.createChildData(this, "child");

        long outOfCodeBlockCount = myTracker.getOutOfCodeBlockModificationCount();
        child.move(this, dir2);
        assertFalse(myTracker.getOutOfCodeBlockModificationCount() == outOfCodeBlockCount);

        outOfCodeBlockCount = myTracker.getOutOfCodeBlockModificationCount();
        child.move(this, dir1);
        assertFalse(myTracker.getOutOfCodeBlockModificationCount() == outOfCodeBlockCount);
      }
    }.execute();
  }

  public void testMoveDir() {
    new WriteAction<Object>() {
      @Override
      protected void run(@NotNull Result<Object> result) throws Throwable {
        final VirtualFile dir1 = getProject().getBaseDir().createChildDirectory(this, "dir1");
        final VirtualFile dir2 = getProject().getBaseDir().createChildDirectory(this, "dir2");
        VirtualFile child = dir1.createChildDirectory(this, "child");

        long outOfCodeBlockCount = myTracker.getOutOfCodeBlockModificationCount();
        child.move(this, dir2);
        assertFalse(myTracker.getOutOfCodeBlockModificationCount() == outOfCodeBlockCount);

        outOfCodeBlockCount = myTracker.getOutOfCodeBlockModificationCount();
        child.move(this, dir1);
        assertFalse(myTracker.getOutOfCodeBlockModificationCount() == outOfCodeBlockCount);
      }
    }.execute();
  }
}
