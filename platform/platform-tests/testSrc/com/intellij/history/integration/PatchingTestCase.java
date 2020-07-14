// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.VfsTestUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class PatchingTestCase extends IntegrationTestCase {
  protected Path patchFilePath;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    patchFilePath = getTempDir().newPath("f.patch");
  }

  protected void clearRoot() {
    for (VirtualFile f : myRoot.getChildren()) {
      VfsTestUtil.deleteFile(f);
    }
  }

  protected void applyPatch() throws Exception {
    PatchReader reader = new PatchReader(patchFilePath);
    List<FilePatch> patches = new ArrayList<>(reader.readTextPatches());
    new PatchApplier(myProject, myRoot, patches, null, null).execute();
  }

  protected static void createChildDataWithContent(@NotNull VirtualFile dir, @NotNull String name) {
    createChildData(dir, name);
    VirtualFile file = dir.findChild(name);
    setFileText(file, "some content");
  }

  protected static void createChildDataWithoutContent(@NotNull VirtualFile dir, @NotNull String name) {
    createChildData(dir, name);
    dir.findChild(name);
  }
}
