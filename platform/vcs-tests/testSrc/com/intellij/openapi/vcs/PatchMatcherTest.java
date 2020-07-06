// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.vcs.changes.patch.AbstractFilePatchInProgress;
import com.intellij.openapi.vcs.changes.patch.MatchPatchPaths;
import com.intellij.project.ProjectKt;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.io.PathKt;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class PatchMatcherTest extends HeavyPlatformTestCase {
  public void testMatchPathAboveProject() {
    Path ioFile = ProjectKt.getStateStore(myProject).getProjectBasePath().getParent().resolve("file.txt");
    PathKt.createFile(ioFile);
    TextFilePatch patch = PatchAutoInitTest.create("../file.txt");

    MatchPatchPaths iterator = new MatchPatchPaths(myProject);
    List<AbstractFilePatchInProgress> filePatchInProgresses = iterator.execute(Collections.singletonList(patch));

    assertEquals(1, filePatchInProgresses.size());
    assertEquals(ioFile.getParent(), filePatchInProgresses.get(0).getBase().toNioPath());
  }
}
