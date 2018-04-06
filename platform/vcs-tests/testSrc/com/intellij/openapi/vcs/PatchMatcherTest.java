// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.vcs.changes.patch.AbstractFilePatchInProgress;
import com.intellij.openapi.vcs.changes.patch.MatchPatchPaths;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.PatchAutoInitTest.create;

public class PatchMatcherTest extends PlatformTestCase {
  public void testMatchPathAboveProject() {
    final VirtualFile root = myProject.getBaseDir();
    VirtualFile vf = createChildData(root.getParent(), "file.txt");

    final File ioFile = VfsUtilCore.virtualToIoFile(vf);
    assertNotNull(ioFile);
    myFilesToDelete.add(ioFile);
    final TextFilePatch patch = create("../file.txt");

    final MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    final List<AbstractFilePatchInProgress> filePatchInProgresses = iterator.execute(Collections.singletonList(patch));

    assertEquals(1, filePatchInProgresses.size());
    assertEquals(root.getParent(), filePatchInProgresses.get(0).getBase());
  }
}
