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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.junit.Before;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class VcsExcludedFileProcessingTest extends PlatformTestCase {
  private ProjectLevelVcsManagerImpl myVcsManager;
  private MockAbstractVcs myVcs;

  @Before
  public void setUp() throws Exception {
    super.setUp();

    myVcs = new MockAbstractVcs(myProject);
    myVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    myVcsManager.registerVcs(myVcs);
  }

  public void testFileUnderExcludedRoot() throws IOException {
    VirtualFile root = getVirtualFile(createTempDir("content"));
    myVcsManager.setDirectoryMapping(root.getPath(), myVcs.getName());
    PsiTestUtil.addContentRoot(myModule, root);
    VirtualFile excludedDir = createChildDirectory(root, "excluded");
    VirtualFile excludedFile = createChildData(excludedDir, "a.txt");
    PsiTestUtil.addExcludedRoot(myModule, excludedDir);

    assertEquals(root, myVcsManager.getVcsRootFor(excludedFile));

    final List<VirtualFile> processed = new ArrayList<>();
    myVcsManager.iterateVcsRoot(root, path -> {
      processed.add(path.getVirtualFile());
      return true;
    });
    assertTrue(processed.contains(excludedFile));
  }
}
