// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeImpl;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.vcs.FileBasedTest;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author irengrig
 */
public class VcsDirtyScopeTest extends FileBasedTest {
  private MockAbstractVcs myVcs;
  private ProjectLevelVcsManagerImpl myVcsManager;

  @Override
  @Before
  public void before() throws Exception {
    super.before();

    myVcs = new MockAbstractVcs(myProject);
    myVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    myVcsManager.registerVcs(myVcs);
    myVcsManager.setDirectoryMapping(myProjectFixture.getProject().getBasePath(), myVcs.getName());
    myVcsManager.waitForInitialized();
  }

  @Override
  @After
  public void after() throws Exception {
    myVcsManager = null;
    myVcs = null;
    super.after();
  }

  private static class Data {
    private VirtualFile baseDir;
    private VirtualFile dir1;
    private VirtualFile dir2;
    private VirtualFile dir3;
    private VirtualFile dir4;
    private VirtualFile innerDir1;
    private VirtualFile innerDir2;
    private List<VirtualFile> files;
  }

  @Test
  public void testVcsIterator() throws Exception {
    final Data data = createData();
    final MockAbstractVcs another = new MockAbstractVcs(myProject, "ANOTHER");
    myVcsManager.registerVcs(another);
    final List<VcsDirectoryMapping> mappings = new ArrayList<>(myVcsManager.getDirectoryMappings());
    mappings.add(new VcsDirectoryMapping(data.dir1.getPath(), another.getKeyInstanceMethod().getName()));
    myVcsManager.setDirectoryMappings(mappings);

    myVcsManager.iterateVcsRoot(myProject.getBaseDir(), path -> {
      Assert.assertFalse(String.format("data dir: %s - file: %s", data.dir1.getPath(), path.getVirtualFile().getPath()),
                         VfsUtilCore.isAncestor(data.dir1, path.getVirtualFile(), false));
      Assert.assertEquals(myVcsManager.getVcsFor(path), myVcs);
      return true;
    });
  }

  @Test
  public void testRecursivelyDirtyDirectoriesUnderNonRecursively() throws Exception {
    final Data data = createData();

    final VcsDirtyScopeImpl scope = new VcsDirtyScopeImpl(new MockAbstractVcs(myProject));

    scope.addDirtyPathFast(data.baseDir, VcsUtil.getFilePath(data.dir1), true);
    scope.addDirtyPathFast(data.baseDir, VcsUtil.getFilePath(data.dir2), true);
    scope.addDirtyPathFast(data.baseDir, VcsUtil.getFilePath(data.baseDir), false);
    scope.pack();
    final Set<FilePath> dirtyDirs = scope.getRecursivelyDirtyDirectories();
    final Set<FilePath> dirtyFiles = scope.getDirtyFilesNoExpand();

    Assert.assertNotNull(dirtyDirs);
    Assert.assertNotNull(dirtyFiles);
    Assert.assertTrue(dirtyFiles.toString(), dirtyFiles.contains(VcsUtil.getFilePath(data.baseDir)));
    Assert.assertTrue(dirtyDirs.toString(), dirtyDirs.contains(VcsUtil.getFilePath(data.dir1)));
    Assert.assertTrue(dirtyDirs.toString(), dirtyDirs.contains(VcsUtil.getFilePath(data.dir2)));
  }

  private Data createData() throws IOException {
    final Data data = new Data();
    data.baseDir = PlatformTestUtil.getOrCreateProjectTestBaseDir(myProjectFixture.getProject());
    final IOException[] exc = new IOException[1];
    final File ioFile = new File(data.baseDir.getPath());
    final File[] files = ioFile.listFiles();
    for (File file : files) {
      FileUtil.delete(file);
    }
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                data.dir1 = data.baseDir.createChildDirectory(this, "dir1");
                data.dir2 = data.baseDir.createChildDirectory(this, "dir2");
                data.dir3 = data.baseDir.createChildDirectory(this, "dir3");
                data.dir4 = data.baseDir.createChildDirectory(this, "dir4");

                data.innerDir1 = data.dir1.createChildDirectory(this, "innerDir1");
                data.innerDir2 = data.dir2.createChildDirectory(this, "innerDir2");

                final VirtualFile[] virtualFiles = {data.dir1, data.dir2, data.dir3, data.dir4, data.innerDir1, data.innerDir2};
                int i = 1;
                data.files = new LinkedList<>();
                for (VirtualFile vf : virtualFiles) {
                  data.files.add(vf.createChildData(this, "f" + i + ".txt"));
                  ++i;
                }
              }
              catch (IOException e) {
                exc[0] = e;
              }
            }
          });
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    if (exc[0] != null) {
      throw exc[0];
    }
    return data;
  }
}
