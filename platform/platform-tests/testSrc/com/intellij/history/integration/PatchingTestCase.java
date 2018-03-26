// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration;

import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.PatchVirtualFileReader;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.VfsTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class PatchingTestCase extends IntegrationTestCase {
  protected String patchFilePath;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    File dir = createTempDirectory();
    patchFilePath = new File(dir, "patch").getPath();
  }

  protected void clearRoot() {
    for (VirtualFile f : myRoot.getChildren()) {
      VfsTestUtil.deleteFile(f);
    }
  }

  protected void applyPatch() throws Exception {
    PatchReader reader = PatchVirtualFileReader.create(LocalFileSystem.getInstance().refreshAndFindFileByPath(patchFilePath));

    List<FilePatch> patches = new ArrayList<>(reader.readTextPatches());

    new PatchApplier<BinaryFilePatch>(myProject, myRoot, patches, null, null).execute();
  }

  protected static void createChildDataWithContent(@NotNull VirtualFile dir, @NotNull String name) {
    createChildData(dir, name);
    VirtualFile file = dir.findChild(name);
    setFileText(file, "some content");
  }
}
