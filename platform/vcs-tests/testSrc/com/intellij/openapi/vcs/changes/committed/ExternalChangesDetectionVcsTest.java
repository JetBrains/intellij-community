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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.vcs.AbstractJunitVcsTestCase;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author irengrig
 */
public class ExternalChangesDetectionVcsTest extends AbstractJunitVcsTestCase  {
  private MockAbstractVcs myVcs;
  private ProjectLevelVcsManagerImpl myVcsManager;
  private LocalFileSystem myLFS;
  private ChangeListManager myChangeListManager;
  private VcsDirtyScopeManager myVcsDirtyScopeManager;
  private TempDirTestFixture myTempDirTestFixture;
  private File myClientRoot;

  @Before
  public void setUp() {
    EdtTestUtil.runInEdtAndWait(() -> {
      final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
      myTempDirTestFixture = fixtureFactory.createTempDirTestFixture();
      myTempDirTestFixture.setUp();

      myClientRoot = new File(myTempDirTestFixture.getTempDirPath(), "clientroot");
      myClientRoot.mkdir();

      initProject(myClientRoot, ExternalChangesDetectionVcsTest.this.getTestName());

      myVcs = new MockAbstractVcs(myProject);
      myVcs.setChangeProvider(new MyMockChangeProvider());
      myVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
      myVcsManager.registerVcs(myVcs);
      myVcsManager.setDirectoryMapping("", myVcs.getName());

      myLFS = LocalFileSystem.getInstance();
      myChangeListManager = ChangeListManager.getInstance(myProject);
      myVcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    });
  }

  @After
  public void tearDown() {
    EdtTestUtil.runInEdtAndWait(() -> {
      myVcsManager.unregisterVcs(myVcs);
      myVcs = null;
      myVcsManager = null;
      myChangeListManager = null;
      myVcsDirtyScopeManager = null;
      tearDownProject();
      myTempDirTestFixture.tearDown();
      myTempDirTestFixture = null;
      FileUtil.delete(myClientRoot);
    });
  }

  @Test
  public void testDeletion() throws Exception {
    final File f = new File(myClientRoot, "f.txt");
    f.createNewFile();
    final VirtualFile vf = myLFS.refreshAndFindFileByIoFile(f);
    myChangeListManager.ensureUpToDate(false);
    ((ChangeListManagerImpl) myChangeListManager).getUnversionedFiles().contains(vf);
    FileUtil.delete(f);
    myWorkingCopyDir.refresh(false, true);
    myChangeListManager.ensureUpToDate(false);
    ((ChangeListManagerImpl) myChangeListManager).getUnversionedFiles().isEmpty();
  }

  @Test
  public void testGeneration() throws Exception {
    for (int i = 0; i < 100; i++) {
      final File f = new File(myClientRoot, "f" + i + ".txt");
      f.createNewFile();
    }
    myWorkingCopyDir.refresh(false, true);
    myChangeListManager.ensureUpToDate(false);
    final List<VirtualFile> unversionedFiles = ((ChangeListManagerImpl)myChangeListManager).getUnversionedFiles();
    final Pattern pattern = Pattern.compile("f([0-9])+\\.txt");
    int cnt = 0;
    for (VirtualFile unversionedFile : unversionedFiles) {
      if (VfsUtil.isAncestor(myWorkingCopyDir, unversionedFile, true)) {
        ++ cnt;
        Assert.assertTrue(pattern.matcher(unversionedFile.getName()).matches());
      }
    }
    Assert.assertEquals(100, cnt);
  }

  /*@Test
  public void testMoveDir() throws Exception {
    final File root = new File(myClientRoot, "was");
    root.mkdir();
    for (int i = 0; i < 10; i++) {
      final File dir = new File(root, "dir" + i);
      dir.mkdir();
      for (int j = 0; j < 10; j++) {
        final File f = new File(dir, "f" + j + ".txt");
        f.createNewFile();
      }
    }
    myWorkingCopyDir.refresh(false, true);
    myChangeListManager.ensureUpToDate(false);
    List<VirtualFile> unversionedFiles = ((ChangeListManagerImpl)myChangeListManager).getUnversionedFiles();
    final Pattern pattern = Pattern.compile("f([0-9])+\\.txt");
    final Pattern patternDir = Pattern.compile("dir([0-9])+");
    int cnt = 0;
    for (VirtualFile unversionedFile : unversionedFiles) {
      if (VfsUtil.isAncestor(myWorkingCopyDir, unversionedFile, true) && (! "was".equals(unversionedFile.getName()))) {
        ++ cnt;
        Assert.assertTrue(pattern.matcher(unversionedFile.getName()).matches() || patternDir.matcher(unversionedFile.getName()).matches());
      }
    }
    Assert.assertEquals(110, cnt);
    final File renamedFile = new File(myClientRoot, "newName");
    FileUtil.copyDir(root, renamedFile);
    FileUtil.delete(root);

    myWorkingCopyDir.refresh(false, true);
    myChangeListManager.ensureUpToDate(false);
    unversionedFiles = ((ChangeListManagerImpl)myChangeListManager).getUnversionedFiles();

    cnt = 0;
    for (VirtualFile unversionedFile : unversionedFiles) {
      if (VfsUtil.isAncestor(myWorkingCopyDir, unversionedFile, true) && (! "newName".equals(unversionedFile.getName()))) {
        ++ cnt;
        Assert.assertTrue((pattern.matcher(unversionedFile.getName()).matches() || patternDir.matcher(unversionedFile.getName()).matches()));
        if (unversionedFile.getPath().indexOf("newName") == -1) {
          System.out.println("wrong " + unversionedFile.getPath());
        }
      }
    }
    Assert.assertEquals(110, cnt);
  }*/

  private static class MyMockChangeProvider implements ChangeProvider {
    @Override
    public void doCleanup(List<VirtualFile> files) {
    }

    @Override
    public void getChanges(@NotNull VcsDirtyScope dirtyScope,
                           @NotNull final ChangelistBuilder builder,
                           @NotNull ProgressIndicator progress,
                           @NotNull ChangeListManagerGate addGate) {
      for (FilePath path : dirtyScope.getDirtyFiles()) {
        builder.processUnversionedFile(path.getVirtualFile());
      }
      final Processor<VirtualFile> processor = vf -> {
        builder.processUnversionedFile(vf);
        return true;
      };
      for (FilePath dir : dirtyScope.getRecursivelyDirtyDirectories()) {
        VfsUtil.processFilesRecursively(dir.getVirtualFile(), processor);
      }
    }

    @Override
    public boolean isModifiedDocumentTrackingRequired() {
      return false;
    }
  }
}
