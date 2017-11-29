/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.vcs.changes.patch.AbstractFilePatchInProgress;
import com.intellij.openapi.vcs.changes.patch.MatchPatchPaths;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.PatchAutoInitTest.create;


public class PatchMatcherTest extends PlatformTestCase {
  @Override
  protected File getIprFile() throws IOException {
    return new File(createTempDirectory(), "test.ipr");
  }

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
