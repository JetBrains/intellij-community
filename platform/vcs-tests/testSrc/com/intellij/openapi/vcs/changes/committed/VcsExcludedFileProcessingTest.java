// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
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
