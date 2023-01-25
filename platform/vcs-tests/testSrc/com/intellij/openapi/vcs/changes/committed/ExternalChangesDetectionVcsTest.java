// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.vcs.AbstractJunitVcsTestCase;
import com.intellij.util.Processor;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

public class ExternalChangesDetectionVcsTest extends AbstractJunitVcsTestCase  {
  private MockAbstractVcs myVcs;
  private ProjectLevelVcsManagerImpl myVcsManager;
  private LocalFileSystem myLFS;
  private ChangeListManagerImpl myChangeListManager;
  private VcsDirtyScopeManager myVcsDirtyScopeManager;
  private TempDirTestFixture myTempDirTestFixture;
  private File myClientRoot;

  @Before
  public void setUp() throws Exception {
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
      myVcsManager.setDirectoryMapping(myClientRoot.getPath(), myVcs.getName());

      myLFS = LocalFileSystem.getInstance();
      myChangeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
      myVcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    });
  }

  @After
  public void tearDown() throws Exception {
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
    myChangeListManager.ensureUpToDate();
    myChangeListManager.getUnversionedFiles().contains(vf);
    FileUtil.delete(f);
    myWorkingCopyDir.refresh(false, true);
    myChangeListManager.ensureUpToDate();
    myChangeListManager.getUnversionedFiles().isEmpty();
  }

  @Test
  public void testGeneration() throws Exception {
    for (int i = 0; i < 100; i++) {
      final File f = new File(myClientRoot, "f" + i + ".txt");
      f.createNewFile();
    }
    myWorkingCopyDir.refresh(false, true);
    myChangeListManager.ensureUpToDate();
    final List<VirtualFile> unversionedFiles = myChangeListManager.getUnversionedFiles();
    final Pattern pattern = Pattern.compile("f([0-9])+\\.txt");
    int cnt = 0;
    for (VirtualFile unversionedFile : unversionedFiles) {
      if (VfsUtilCore.isAncestor(myWorkingCopyDir, unversionedFile, true)) {
        ++cnt;
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
    public void getChanges(@NotNull VcsDirtyScope dirtyScope,
                           @NotNull final ChangelistBuilder builder,
                           @NotNull ProgressIndicator progress,
                           @NotNull ChangeListManagerGate addGate) {
      for (FilePath path : dirtyScope.getDirtyFiles()) {
        builder.processUnversionedFile(path);
      }
      final Processor<VirtualFile> processor = vf -> {
        builder.processUnversionedFile(VcsUtil.getFilePath(vf));
        return true;
      };
      for (FilePath dir : dirtyScope.getRecursivelyDirtyDirectories()) {
        VfsUtilCore.processFilesRecursively(dir.getVirtualFile(), processor);
      }
    }

    @Override
    public boolean isModifiedDocumentTrackingRequired() {
      return false;
    }
  }
}
