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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeImpl;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeModifier;
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.FileBasedTest;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author irengrig
 */
public class VcsDirtyScopeTest extends FileBasedTest {
  private MockAbstractVcs myVcs;
  private ProjectLevelVcsManagerImpl myVcsManager;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    myVcs = new MockAbstractVcs(myProject);
    myVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    myVcsManager.registerVcs(myVcs);
    myVcsManager.setDirectoryMapping(myProjectFixture.getProject().getBaseDir().getPath(), myVcs.getName());
  }

  @Override
  @After
  public void tearDown() throws Exception {
    myVcsManager = null;
    myVcs = null;
    super.tearDown();
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
      Assert.assertFalse(VfsUtil.isAncestor(data.dir1, path.getVirtualFile(), false));
      Assert.assertTrue(myVcsManager.getVcsFor(path).equals(myVcs));
      return true;
    });
  }

  @Test
  public void testAddRemove() throws Exception {
    final Data data = createData();

    final VcsDirtyScopeImpl scope = new VcsDirtyScopeImpl(new MockAbstractVcs(myProject), myProject);
    scope.addDirtyDirRecursively(VcsUtil.getFilePath(data.dir1));
    scope.addDirtyDirRecursively(VcsUtil.getFilePath(data.dir3));
    scope.addDirtyDirRecursively(VcsUtil.getFilePath(data.dir4));
    for (VirtualFile file : data.files) {
      scope.addDirtyFile(VcsUtil.getFilePath(file));
    }

    final Set<VirtualFile> set = new HashSet<>();
    set.add(data.dir1);
    set.add(data.dir3);
    set.add(data.dir4);
    set.add(data.files.get(1));
    set.add(data.files.get(5));

    final HashSet<VirtualFile> removed = new HashSet<>(set);
    removeMarked(set, scope, virtualFile -> removed.remove(virtualFile));

    Assert.assertTrue(scope.isEmpty());
    Assert.assertTrue(removed.isEmpty());
  }

  @Test
  public void testRecursivelyDirtyDirectoriesUnderNonRecursively() throws Exception {
    final Data data = createData();

    final VcsDirtyScopeImpl scope = new VcsDirtyScopeImpl(new MockAbstractVcs(myProject), myProject);

    scope.addDirtyData(Arrays.asList(VcsUtil.getFilePath(data.dir1), VcsUtil.getFilePath(data.dir2)),
                       Collections.singletonList(VcsUtil.getFilePath(data.baseDir)));
    final Set<FilePath> dirtyDirs = scope.getRecursivelyDirtyDirectories();
    final Set<FilePath> dirtyFiles = scope.getDirtyFilesNoExpand();

    Assert.assertNotNull(dirtyDirs);
    Assert.assertNotNull(dirtyFiles);
    Assert.assertTrue(dirtyFiles.contains(VcsUtil.getFilePath(data.baseDir)));
    Assert.assertTrue(dirtyDirs.contains(VcsUtil.getFilePath(data.dir1)));
    Assert.assertTrue(dirtyDirs.contains(VcsUtil.getFilePath(data.dir2)));
  }

  private Data createData() throws IOException {
    final Data data = new Data();
    data.baseDir = myProjectFixture.getProject().getBaseDir();
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


  private static void removeMarked(final Set<VirtualFile> ignored,
                                   final VcsModifiableDirtyScope scope,
                                   final Consumer<VirtualFile> listener) {
    final VcsDirtyScopeModifier modifier = scope.getModifier();
    if (modifier != null) {
      final Iterator<FilePath> filesIterator = modifier.getDirtyFilesIterator();
      while (filesIterator.hasNext()) {
        final FilePath dirtyFile = filesIterator.next();
        if ((dirtyFile.getVirtualFile() != null) && ignored.contains(dirtyFile.getVirtualFile())) {
          filesIterator.remove();
          listener.consume(dirtyFile.getVirtualFile());
        }
      }
      final Collection<VirtualFile> roots = modifier.getAffectedVcsRoots();
      for (VirtualFile root : roots) {
        final Iterator<FilePath> dirIterator = modifier.getDirtyDirectoriesIterator(root);
        while (dirIterator.hasNext()) {
          final FilePath dir = dirIterator.next();
          if ((dir.getVirtualFile() != null) && ignored.contains(dir.getVirtualFile())) {
            dirIterator.remove();
            listener.consume(dir.getVirtualFile());
          }
        }
      }
      modifier.recheckDirtyKeys();
    }
  }
}
