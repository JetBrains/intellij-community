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

package com.intellij.history.integration;

import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.PatchVirtualFileReader;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
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

  protected void clearRoot() throws IOException {
    for (VirtualFile f : myRoot.getChildren()) {
      delete(f);
    }
  }

  protected void applyPatch() throws Exception {
    PatchReader reader = PatchVirtualFileReader.create(LocalFileSystem.getInstance().refreshAndFindFileByPath(patchFilePath));

    List<FilePatch> patches = new ArrayList<>(reader.readAllPatches());

    new PatchApplier<BinaryFilePatch>(myProject, myRoot, patches, null, null, null).execute();
  }

  protected static void createChildDataWithContent(@NotNull VirtualFile dir, @NotNull String name) throws IOException {
    createChildData(dir, name);
    VirtualFile file = dir.findChild(name);
    setFileText(file, "some content");
  }
}
