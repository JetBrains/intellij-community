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

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.actions.DescindingFilesFilter;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcsesI;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Arrays;

/**
 * @author yole
 */
public class DirectoryMappingListTest extends PlatformTestCase {
  @NonNls private static final String BASE_PATH = "/vcs/directoryMappings/";
  private NewMappings myMappings;
  private VirtualFile myProjectRoot;
  private String myRootPath;
  private AllVcsesI myVcses;

  @Override
  protected void setUpProject() throws Exception {
    final String root = VcsTestUtil.getTestDataPath() + BASE_PATH;

    myProjectRoot = PsiTestUtil.createTestProjectStructure(getTestName(true),null, FileUtil.toSystemIndependentName(root), myFilesToDelete, false);
    VirtualFile projectFile = myProjectRoot.findChild("directoryMappings.ipr");
    myRootPath = myProjectRoot.getPath();

    myProject = ProjectManagerEx.getInstanceEx().loadProject(projectFile.getPath());
    ProjectManagerEx.getInstanceEx().openTestProject(myProject);
    UIUtil.dispatchAllInvocationEvents(); // startup activities

    final StartupManagerImpl startupManager = (StartupManagerImpl)StartupManager.getInstance(myProject);
    startupManager.runStartupActivities();
    startupManager.startCacheUpdate();
    myVcses = AllVcses.getInstance(myProject);
    myVcses.registerManually(new MockAbstractVcs(myProject, "mock"));
    myVcses.registerManually(new MockAbstractVcs(myProject, "CVS"));
    myVcses.registerManually(new MockAbstractVcs(myProject, "mock2"));
    myMappings = new NewMappings(myProject, (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject),
                                 FileStatusManager.getInstance(myProject));
    startupManager.runPostStartupActivities();
  }

  @Override
  protected void tearDown() throws Exception {
    myMappings.disposeMe();
    ((AllVcses) myVcses).dispose();
    
    super.tearDown();
  }

  public void testMappingsFilter() {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    ((MockAbstractVcs) vcsManager.findVcsByName("mock")).setAllowNestedRoots(true);

    final String[] pathsStr = new String[] {myRootPath + "/a", myRootPath + "/a/b", myRootPath + "/def",
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
                                                  new VcsDirectoryMapping(pathsStr[5], "mock2")) );

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
    myMappings.setMapping(myRootPath + "/a", "CVS");
    myMappings.setMapping(myRootPath + "/a-b", "mock2");
    assertEquals(3, myMappings.getDirectoryMappings().size());
    myMappings.cleanupMappings();
    assertEquals(3, myMappings.getDirectoryMappings().size());
    assertEquals("mock2", myMappings.getVcsFor(myProjectRoot.findChild("a-b")));
    assertEquals("CVS", myMappings.getVcsFor(myProjectRoot.findChild("a")));
  }

  public void testSamePrefixEmpty() {
    myMappings.setMapping(myRootPath + "/a", "CVS");
    assertEquals("", myMappings.getVcsFor(myProjectRoot.findChild("a-b")));
  }

  public void testSame() {
    myMappings.removeDirectoryMapping(new VcsDirectoryMapping("", ""));
    myMappings.setMapping(myRootPath + "/parent/path", "CVS");

    final String[] children = new String[] {
      myRootPath + "/parent/path", myRootPath + "\\parent\\path", myRootPath + "\\parent\\path"
    };
    createFiles(children);

    for (String child : children) {
      myMappings.setMapping(child, "CVS");
      myMappings.cleanupMappings();
      Assert.assertEquals("cleanup failed: " + child, 1, myMappings.getDirectoryMappings().size());
    }

    for (String child : children) {
      myMappings.setMapping(child, "CVS");
      Assert.assertEquals("cleanup failed: " + child, 1, myMappings.getDirectoryMappings().size());
    }
  }

  public void testHierarchy() {
    myMappings.removeDirectoryMapping(new VcsDirectoryMapping("", ""));
    myMappings.setMapping(myRootPath + "/parent", "CVS");

    final String[] children = new String[] {
      myRootPath + "/parent/child1", myRootPath + "/parent/middle/child2", myRootPath + "/parent/middle/child3"
    };
    createFiles(children);

    for (String child : children) {
      myMappings.setMapping(child, "CVS");
      myMappings.cleanupMappings();
      Assert.assertEquals("cleanup failed: " + child, 1, myMappings.getDirectoryMappings().size());
    }
  }

  public void testNestedInnerCopy() {
    myMappings.removeDirectoryMapping(new VcsDirectoryMapping("", ""));
    myMappings.setMapping(myRootPath + "/parent", "CVS");
    myMappings.setMapping(myRootPath + "/parent/child", "mock");

    final String[] children = new String[] {
      myRootPath + "/parent/child1", myRootPath + "\\parent\\middle\\child2", myRootPath + "/parent/middle/child3",
      myRootPath + "/parent/child/inner"
    };
    createFiles(children);

    final String[] awaitedVcsNames = {"CVS","CVS","CVS","mock"};
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    for (int i = 0; i < children.length; i++) {
      String child = children[i];
      final VirtualFile vf = lfs.refreshAndFindFileByIoFile(new File(child));
      Assert.assertNotNull(vf);
      final VcsDirectoryMapping mapping = myMappings.getMappingFor(vf);
      Assert.assertNotNull(mapping);
      Assert.assertEquals(awaitedVcsNames[i], mapping.getVcs());
    }
  }

  private static void createFiles(final String[] paths) {
    for (String path : paths) {
      final File file = new File(FileUtil.toSystemDependentName(path));
      assert file.mkdirs() || file.isDirectory() : file;
      myFilesToDelete.add(file);
    }
  }
}
