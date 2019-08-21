// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.actions.DescindingFilesFilter;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcsesI;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Arrays;

/**
 * @author yole
 */
public class DirectoryMappingListTest extends HeavyPlatformTestCase {
  @NonNls private static final String BASE_PATH = "/vcs/directoryMappings/";
  private NewMappings myMappings;
  private VirtualFile myProjectRoot;
  private String myRootPath;

  @Override
  protected void setUpProject() throws Exception {
    final String root = FileUtil.toSystemIndependentName(VcsTestUtil.getTestDataPath() + BASE_PATH);

    myProjectRoot = PsiTestUtil.createTestProjectStructure(getTestName(true), null, root, myFilesToDelete, false);
    VirtualFile projectFile = myProjectRoot.findChild("directoryMappings.ipr");
    myRootPath = myProjectRoot.getPath();

    myProject = ProjectManagerEx.getInstanceEx().loadProject(projectFile.getPath());
    ProjectManagerEx.getInstanceEx().openTestProject(myProject);
    UIUtil.dispatchAllInvocationEvents(); // startup activities

    StartupManagerImpl startupManager = (StartupManagerImpl)StartupManager.getInstance(myProject);
    startupManager.runStartupActivities();
    AllVcsesI vcses = AllVcses.getInstance(myProject);
    vcses.registerManually(new MockAbstractVcs(myProject, "mock"));
    vcses.registerManually(new MockAbstractVcs(myProject, "CVS"));
    vcses.registerManually(new MockAbstractVcs(myProject, "mock2"));

    ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    myMappings = new NewMappings(myProject, vcsManager,
                                 FileStatusManager.getInstance(myProject), DefaultVcsRootPolicy.getInstance(myProject));
    Disposer.register(getTestRootDisposable(), myMappings);
    startupManager.runPostStartupActivities();
    vcsManager.waitForInitialized();
  }

  public void testMappingsFilter() {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    ((MockAbstractVcs)vcsManager.findVcsByName("mock")).setAllowNestedRoots(true);

    final String[] pathsStr = {myRootPath + "/a", myRootPath + "/a/b", myRootPath + "/def",
      myRootPath + "/a-b", myRootPath + "/a-b/d-e", myRootPath + "/a-b1/d-e"};
    final VirtualFile a = myProjectRoot.findChild("a");
    createChildDirectory(a, "b");
    createChildDirectory(myProjectRoot, "def");
    final VirtualFile ab = myProjectRoot.findChild("a-b");
    final VirtualFile ab1 = createChildDirectory(myProjectRoot, "a-b1");
    createChildDirectory(ab, "d-e");
    createChildDirectory(ab1, "d-e");

    vcsManager.setDirectoryMappings(Arrays.asList(new VcsDirectoryMapping(pathsStr[0], "mock"),
                                                  new VcsDirectoryMapping(pathsStr[1], "mock"),
                                                  new VcsDirectoryMapping(pathsStr[2], "mock"),
                                                  new VcsDirectoryMapping(pathsStr[3], "mock2"),
                                                  new VcsDirectoryMapping(pathsStr[4], "mock2"),
                                                  new VcsDirectoryMapping(pathsStr[5], "mock2")));

    final FilePath[] paths = new FilePath[6];
    for (int i = 0; i < pathsStr.length; i++) {
      final String s = pathsStr[i];
      paths[i] = VcsUtil.getFilePath(s, true);
    }

    assertEquals(6, vcsManager.getDirectoryMappings().size());
    final FilePath[] filePaths = DescindingFilesFilter.filterDescindingFiles(paths, myProject);
    assertEquals(5, filePaths.length);
  }

  public void testSamePrefix() {
    VirtualFile childA = myProjectRoot.findChild("a");
    VirtualFile childAB = myProjectRoot.findChild("a-b");
    assertNotNull(childA);
    assertNotNull(childAB);

    myMappings.setMapping(myRootPath + "/a", "CVS");
    myMappings.setMapping(myRootPath + "/a-b", "mock2");
    assertEquals(2, myMappings.getDirectoryMappings().size());
    myMappings.cleanupMappings();
    assertEquals(2, myMappings.getDirectoryMappings().size());
    assertEquals("mock2", getVcsFor(childAB));
    assertEquals("CVS", getVcsFor(childA));
  }

  public void testSamePrefixEmpty() {
    VirtualFile childAB = myProjectRoot.findChild("a-b");
    assertNotNull(childAB);

    myMappings.setMapping(myRootPath + "/a", "CVS");
    assertNull(getVcsFor(childAB));
  }

  public void testSame() {
    myMappings.setMapping(myRootPath + "/parent/path1", "CVS");
    myMappings.setMapping(myRootPath + "\\parent\\path2", "CVS");

    final String[] children = {
      myRootPath + "\\parent\\path1", myRootPath + "/parent/path1", myRootPath + "\\parent\\path1",
      myRootPath + "\\parent\\path2", myRootPath + "/parent/path2", myRootPath + "\\parent\\path2"
    };
    createFiles(children);

    for (String child : children) {
      myMappings.setMapping(child, "CVS");
      myMappings.cleanupMappings();
      assertEquals("cleanup failed: " + child, 2, myMappings.getDirectoryMappings().size());
    }

    for (String child : children) {
      myMappings.setMapping(child, "CVS");
      assertEquals("cleanup failed: " + child, 2, myMappings.getDirectoryMappings().size());
    }
  }

  public void testHierarchy() {
    myMappings.setMapping(myRootPath + "/parent", "CVS");

    final String[] children = {
      myRootPath + "/parent/child1", myRootPath + "/parent/middle/child2", myRootPath + "/parent/middle/child3"
    };
    createFiles(children);

    for (String child : children) {
      myMappings.setMapping(child, "CVS");
      myMappings.cleanupMappings();
      assertEquals("cleanup failed: " + child, 1, myMappings.getDirectoryMappings().size());
    }
  }

  public void testNestedInnerCopy() {
    myMappings.setMapping(myRootPath + "/parent", "CVS");
    myMappings.setMapping(myRootPath + "/parent/child", "mock");

    final String[] children = {
      myRootPath + "/parent/child1",
      myRootPath + "\\parent\\middle\\child2",
      myRootPath + "/parent/middle/child3",
      myRootPath + "/parent/child/inner"
    };
    createFiles(children);

    myMappings.waitMappedRootsUpdate();

    final String[] awaitedVcsNames = {"CVS", "CVS", "CVS", "mock"};
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    for (int i = 0; i < children.length; i++) {
      String child = children[i];
      final VirtualFile vf = lfs.refreshAndFindFileByIoFile(new File(child));
      assertNotNull("No file for: " + child, vf);
      final VcsDirectoryMapping mapping = getMappingFor(vf);
      assertNotNull("No mapping for: " + vf, mapping);
      assertEquals(awaitedVcsNames[i], mapping.getVcs());
    }
  }

  private void createFiles(final String[] paths) {
    for (String path : paths) {
      final File file = new File(FileUtil.toSystemDependentName(path));
      boolean created = file.mkdirs();
      assertTrue("Can't create file: " + file, created || file.isDirectory());
      myFilesToDelete.add(file);
    }
    LocalFileSystem.getInstance().refreshIoFiles(myFilesToDelete);
  }

  private String getVcsFor(VirtualFile file) {
    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    AbstractVcs vcs = root != null ? root.vcs : null;
    return vcs != null ? vcs.getName() : null;
  }

  private VcsDirectoryMapping getMappingFor(VirtualFile file) {
    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.mapping : null;
  }
}
