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

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.changes.ui.IgnoreUnversionedDialog;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class IgnoreIdeaLevelTest extends PlatformTestCase {
  private MockAbstractVcs myVcs;
  private ProjectLevelVcsManagerImpl myVcsManager;
  private ChangeListManagerImpl myClManager;
  private IgnoreUnversionedDialog dialog;
  private ModuleManager myModuleManager;
  private Module myOutsideModule;
  private VirtualFile myModuleRoot;
  private File myModuleRootFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myVcs = new MockAbstractVcs(getProject());
    myModuleManager = ModuleManager.getInstance(getProject());
    createOutsideModule();

    myVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(getProject());
    myVcsManager.registerVcs(myVcs);
    myVcsManager.setDirectoryMapping("", myVcs.getName());
    myVcsManager.setDirectoryMapping(myModuleRootFile.getAbsolutePath(), myVcs.getName());
    myVcsManager.updateActiveVcss();

    myClManager = ChangeListManagerImpl.getInstanceImpl(myProject);

    dialog = new IgnoreUnversionedDialog(myProject);
    Disposer.register(getTestRootDisposable(), dialog.getDisposable());
  }

  private void createOutsideModule() {
    final VirtualFile baseDir = myProject.getBaseDir();
    assertNotNull(baseDir);
    final VirtualFile baseParent = baseDir.getParent();
    assertNotNull(baseParent);
    myModuleRootFile = new File(baseParent.getPath().replace('/', File.separatorChar), "outside");
    final File moduleFile = new File(myModuleRootFile, "outside" + ModuleFileType.DOT_DEFAULT_EXTENSION);
    try {
      myModuleRootFile.mkdir();
      moduleFile.createNewFile();
    }
    catch (IOException e) {
      LOG.error(e);
    }

    myModuleRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myModuleRootFile);
    myFilesToDelete.add(myModuleRootFile);
    myFilesToDelete.add(moduleFile);
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleFile);
    ApplicationManager.getApplication().runWriteAction(() -> {
      myOutsideModule = myModuleManager.newModule(virtualFile.getPath(), ModuleTypeId.JAVA_MODULE);
      myOutsideModule.getModuleFile();
    });
  }

  @Override
  protected void tearDown() throws Exception {
    myVcsManager.unregisterVcs(myVcs);

    UsefulTestCase.clearDeclaredFields(this, IgnoreIdeaLevelTest.class);

    super.tearDown();
  }

  // translates ignored path as if it was entered into a dialog
  private IgnoredFileBean translate(final IgnoredFileBean bean) {
    dialog.setIgnoredFile(bean);
    return dialog.getSelectedIgnoredFiles()[0];
  }

  private static class FileStructure {
    private VirtualFile myABase;
    private VirtualFile myBBase;
    private VirtualFile myCBase;
    private VirtualFile myF1Base;
    private VirtualFile myF2Base;
    private VirtualFile myF3Base;
    private VirtualFile myF4Base;

    private VirtualFile myAOutside;
    private VirtualFile myBOutside;
    private VirtualFile myCOutside;
    private VirtualFile myF1Outside;
    private VirtualFile myF2Outside;
    private VirtualFile myF3Outside;
    private VirtualFile myF4Outside;

    private FileStructure(final VirtualFile baseDir, final VirtualFile outsideDir) {
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) {
          try {
            myABase = baseDir.createChildDirectory(this, "a");
            myBBase = myABase.createChildDirectory(this, "b");
            myCBase = myABase.createChildDirectory(this, "c");
            myF1Base = myBBase.createChildData(this, "f1.txt");
            myF2Base = myBBase.createChildData(this, "f2.txt");
            myF3Base = myCBase.createChildData(this, "f3.txt");
            myF4Base = myCBase.createChildData(this, "f4.txt");

            myAOutside = outsideDir.createChildDirectory(this, "a");
            myBOutside = myAOutside.createChildDirectory(this, "b");
            myCOutside = myAOutside.createChildDirectory(this, "c");
            myF1Outside = myBOutside.createChildData(this, "f1.txt");
            myF2Outside = myBOutside.createChildData(this, "f2.txt");
            myF3Outside = myCOutside.createChildData(this, "f3.txt");
            myF4Outside = myCOutside.createChildData(this, "f4.txt");
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }.execute().throwException();
    }
  }

  public void testRoots() {
    assertEquals(2, myVcsManager.getAllVcsRoots().length);
  }

  public void testIgnoreBigAmountOfExactFiles() throws Exception {
    File baseFile = new File(myProject.getBaseDir().getPath());
    final Set<File> isIgnoredYes = new HashSet<>();
    final Set<File> isIgnoredNo = new HashSet<>();

    int N = 20000;
    for (int i = 0; i < N; i++) {
      File file = new File(baseFile, "tmp" + i);
      file.createNewFile();
      if (i % 2 == 0) {
        final IgnoredFileBean bean = translate(IgnoredBeanFactory.ignoreFile(FileUtil.toSystemIndependentName(file.getPath()), myProject));
        myClManager.addFilesToIgnore(bean);
        if (i > N / 2 && isIgnoredYes.size() < 10) {
          isIgnoredYes.add(file);
        }
      }
      else if (i > N / 2 && isIgnoredNo.size() < 10) {
        isIgnoredNo.add(file);
      }
    }

    LOG.debug("Files created");

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final long start = System.currentTimeMillis();
    for (File file : isIgnoredNo) {
      final VirtualFile vf = lfs.refreshAndFindFileByIoFile(file);
      Assert.assertNotNull(vf);
      final boolean file1 = myClManager.isIgnoredFile(vf);
      Assert.assertFalse(file1);
    }
    for (File file : isIgnoredYes) {
      final VirtualFile vf = lfs.refreshAndFindFileByIoFile(file);
      Assert.assertNotNull(vf);
      final boolean file1 = myClManager.isIgnoredFile(vf);
      Assert.assertTrue(file1);
    }
    final long end = System.currentTimeMillis();
    LOG.debug("Millis passed: " + (end - start));
  }

  @Test
  public void testSimple() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);

    final IgnoredFileBean bean1 = IgnoredBeanFactory.ignoreFile(fs.myF1Base.getPath(), myProject);
    final IgnoredFileBean bean2 = IgnoredBeanFactory.ignoreFile(fs.myF2Outside.getPath(), myProject);

    final IgnoredFileBean translated1 = translate(bean1);
    final IgnoredFileBean translated2 = translate(bean2);

    myClManager.setFilesToIgnore(translated1, translated2);

    printIgnored();
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF1Base));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF3Outside));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF2Outside));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF3Base));
  }

  @Test
  public void testSimpleStrasse() {
    final VirtualFile file = createFileInCommand(myProject.getBaseDir(), "Straße", "123");
    final VirtualFile file2 = createFileInCommand(myProject.getBaseDir(), "Strasse", "123");

    final IgnoredFileBean bean1 = IgnoredBeanFactory.ignoreFile(file.getPath(), myProject);

    final IgnoredFileBean translated1 = translate(bean1);

    myClManager.setFilesToIgnore(translated1);

    printIgnored();
    Assert.assertTrue(myClManager.isIgnoredFile(file));
    Assert.assertFalse(myClManager.isIgnoredFile(file2));
  }

  @Test
  public void testPatterns() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);

    final IgnoredFileBean bean1 = IgnoredBeanFactory.withMask("*1.txt");
    final IgnoredFileBean translated1 = translate(bean1);
    myClManager.setFilesToIgnore(translated1);

    printIgnored();
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF1Base));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF3Outside));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF1Outside));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF3Base));
  }

  @Test
  public void testDirs() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);

    final IgnoredFileBean bean1 = IgnoredBeanFactory.ignoreUnderDirectory(fs.myBBase.getPath(), myProject);
    final IgnoredFileBean bean2 = IgnoredBeanFactory.ignoreUnderDirectory(fs.myBOutside.getPath(), myProject);
    final IgnoredFileBean translated1 = translate(bean1);
    final IgnoredFileBean translated2 = translate(bean2);

    myClManager.setFilesToIgnore(translated1, translated2);

    printIgnored();
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF1Base));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF2Base));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myBBase));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myCBase));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF3Base));

    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF1Outside));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF2Outside));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myBOutside));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myCOutside));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF3Outside));
  }

  @Test
  public void testTypedAbsolute() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);

    String baseUnder = myProject.getBaseDir().getPath();
    if (!baseUnder.endsWith("/")) {
      baseUnder += "/";
    }

    final IgnoredFileBean bean1 = IgnoredBeanFactory.ignoreUnderDirectory(baseUnder + "a/b", myProject);
    final IgnoredFileBean bean2 = IgnoredBeanFactory.ignoreFile(baseUnder + "a/c/f3.txt", myProject);

    myClManager.setFilesToIgnore(bean1, bean2);

    Assert.assertTrue(myClManager.isIgnoredFile(fs.myBBase));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF1Base));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF3Base));

    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF4Base));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myCBase));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF4Outside));
  }

  @Test
  public void testTypedAbsoluteSeparator() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);

    String baseUnder = myProject.getBaseDir().getPath();
    if (!baseUnder.endsWith("/")) {
      baseUnder += "/";
    }
    baseUnder = baseUnder.replace('/', File.separatorChar);

    final IgnoredFileBean bean1 = IgnoredBeanFactory.ignoreUnderDirectory(baseUnder + "a" + File.separator + "b", myProject);
    final IgnoredFileBean bean2 = IgnoredBeanFactory.ignoreFile(baseUnder + "a" + File.separator + "c" + File.separator + "f3.txt", myProject);

    myClManager.setFilesToIgnore(bean1, bean2);

    Assert.assertTrue(myClManager.isIgnoredFile(fs.myBBase));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF1Base));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF3Base));

    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF4Base));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myCBase));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF4Outside));
  }

  @Test
  public void testTypedRelativeInsideSeparator() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);

    final IgnoredFileBean bean1 = IgnoredBeanFactory.ignoreUnderDirectory("a" + File.separator + "b", myProject);
    final IgnoredFileBean bean2 = IgnoredBeanFactory.ignoreFile("a" + File.separator + "c" + File.separator + "f3.txt", myProject);

    myClManager.setFilesToIgnore(bean1, bean2);

    Assert.assertTrue(myClManager.isIgnoredFile(fs.myBBase));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF1Base));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF3Base));

    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF4Base));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myCBase));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF4Outside));
  }

  @Test
  public void testTypedRelativeInside() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);

    final IgnoredFileBean bean1 = IgnoredBeanFactory.ignoreUnderDirectory("a/b", myProject);
    final IgnoredFileBean bean2 = IgnoredBeanFactory.ignoreFile("a/c/f3.txt", myProject);

    myClManager.setFilesToIgnore(bean1, bean2);

    Assert.assertTrue(myClManager.isIgnoredFile(fs.myBBase));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF1Base));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF3Base));

    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF4Base));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myCBase));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF4Outside));
  }

  @Test
  public void testTypedRelativeOutsideSeparator() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);

    String baseUnder = myModuleRoot.getPath();
    if (!baseUnder.endsWith("/")) {
      baseUnder += "/";
    }
    baseUnder = baseUnder.replace('/', File.separatorChar);

    final IgnoredFileBean bean1 = IgnoredBeanFactory.ignoreUnderDirectory(baseUnder + "a" + File.separator + "b", myProject);
    final IgnoredFileBean bean2 = IgnoredBeanFactory.ignoreFile(baseUnder + "a" + File.separator + "c" + File.separator + "f3.txt", myProject);

    myClManager.setFilesToIgnore(bean1, bean2);

    Assert.assertTrue(myClManager.isIgnoredFile(fs.myBOutside));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF1Outside));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF3Outside));

    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF4Outside));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myCOutside));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF4Base));
  }

  @Test
  public void testTypedRelativeOutside() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);

    String baseUnder = myModuleRoot.getPath();
    if (!baseUnder.endsWith("/")) {
      baseUnder += "/";
    }

    final IgnoredFileBean bean1 = IgnoredBeanFactory.ignoreUnderDirectory(baseUnder + "a/b", myProject);
    final IgnoredFileBean bean2 = IgnoredBeanFactory.ignoreFile(baseUnder + "a/c/f3.txt", myProject);

    myClManager.setFilesToIgnore(bean1, bean2);

    Assert.assertTrue(myClManager.isIgnoredFile(fs.myBOutside));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF1Outside));
    Assert.assertTrue(myClManager.isIgnoredFile(fs.myF3Outside));

    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF4Outside));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myCOutside));
    Assert.assertFalse(myClManager.isIgnoredFile(fs.myF4Base));
  }

  @Test
  public void testDoNotAddAlreadyIgnoredDirectory() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);
    myClManager.addDirectoryToIgnoreImplicitly(fs.myABase.getPath());
    myClManager.addDirectoryToIgnoreImplicitly(fs.myBBase.getPath());
    myClManager.addDirectoryToIgnoreImplicitly(fs.myCBase.getPath());
    ConvertExcludedToIgnoredTest.assertIgnoredDirectories(myProject, fs.myABase);
  }

  @Test
  public void testRemoveChildIgnoredDirectoryWhenParentIsAdded() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);
    myClManager.addDirectoryToIgnoreImplicitly(fs.myBBase.getPath());
    myClManager.addDirectoryToIgnoreImplicitly(fs.myCBase.getPath());
    myClManager.addDirectoryToIgnoreImplicitly(fs.myABase.getPath());
    ConvertExcludedToIgnoredTest.assertIgnoredDirectories(myProject, fs.myABase);
  }

  @Test
  public void testManuallyRemovedFromIgnored() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);
    myClManager.getIgnoredFilesComponent().setDirectoriesManuallyRemovedFromIgnored(Collections.singleton(fs.myBBase.getPath()));
    myClManager.addDirectoryToIgnoreImplicitly(fs.myBBase.getPath());
    myClManager.addDirectoryToIgnoreImplicitly(fs.myCBase.getPath());
    ConvertExcludedToIgnoredTest.assertIgnoredDirectories(myProject, fs.myCBase);
  }

  @Test
  public void testRemovingFromImplicitlyIgnored() {
    final FileStructure fs = new FileStructure(myProject.getBaseDir(), myModuleRoot);
    myClManager.addDirectoryToIgnoreImplicitly(fs.myBBase.getPath());
    myClManager.addDirectoryToIgnoreImplicitly(fs.myCBase.getPath());
    ConvertExcludedToIgnoredTest.assertIgnoredDirectories(myProject, fs.myCBase, fs.myBBase);
    
    myClManager.removeImplicitlyIgnoredDirectory(fs.myCBase.getPath());
    ConvertExcludedToIgnoredTest.assertIgnoredDirectories(myProject, fs.myBBase);

    // removing parent directory exclude should not affect child excludes
    myClManager.removeImplicitlyIgnoredDirectory(fs.myBBase.getParent().getPath());
    ConvertExcludedToIgnoredTest.assertIgnoredDirectories(myProject, fs.myBBase);
  }

  private void printIgnored() {
    final IgnoredFileBean[] filesToIgnore = myClManager.getFilesToIgnore();
    LOG.debug("Ignored:");
    for (IgnoredFileBean bean : filesToIgnore) {
      if (IgnoreSettingsType.MASK.equals(bean.getType())) {
        LOG.debug(bean.getMask());
      }
      else {
        LOG.debug(bean.getPath());
      }
    }
  }

  public VirtualFile createFileInCommand(final VirtualFile parent, final String name, @Nullable final String content) {
    return VcsTestUtil.createFile(myProject, parent, name, content);
  }
}
