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
package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.vcs.test.VcsPlatformTest;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.mkdir;

public abstract class VcsRootBaseTest extends VcsPlatformTest {

  static final String DOT_MOCK = ".mock";

  protected MockAbstractVcs myVcs;
  protected String myVcsName;
  protected VirtualFile myRepository;

  private VcsRootChecker myExtension;
  protected RootModelImpl myRootModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    cd(projectRoot);
    Module module = doCreateRealModuleIn("foo", myProject, EmptyModuleType.getInstance());
    myRootModel = ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).getRootModel();
    mkdir("repository");
    projectRoot.refresh(false, true);
    myRepository = projectRoot.findChild("repository");

    myVcs = new MockAbstractVcs(myProject);
    ExtensionPoint<VcsRootChecker> point = getExtensionPoint();
    myExtension = new VcsRootChecker() {
      @NotNull
      @Override
      public VcsKey getSupportedVcs() {
        return myVcs.getKeyInstanceMethod();
      }

      @Override
      public boolean isRoot(@NotNull String path) {
        return new File(path, DOT_MOCK).exists();
      }

      @Override
      public boolean isVcsDir(@NotNull String path) {
        return path.toLowerCase().endsWith(DOT_MOCK);
      }
    };
    point.registerExtension(myExtension);
    vcsManager.registerVcs(myVcs);
    myVcsName = myVcs.getName();
    myRepository.refresh(false, true);
  }

  private static ExtensionPoint<VcsRootChecker> getExtensionPoint() {
    return Extensions.getRootArea().getExtensionPoint(VcsRootChecker.EXTENSION_POINT_NAME);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      getExtensionPoint().unregisterExtension(myExtension);
      vcsManager.unregisterVcs(myVcs);
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * Creates the necessary temporary directories in the filesystem with empty ".mock" directories for given roots.
   * And creates an instance of the project.
   *
   * @param mockRoots path to actual .mock roots, relative to the project dir.
   */
  public void initProject(@NotNull VcsRootConfiguration vcsRootConfiguration)
    throws IOException {
    createDirs(vcsRootConfiguration.getVcsRoots());
    Collection<String> contentRoots = vcsRootConfiguration.getContentRoots();
    createProjectStructure(myProject, contentRoots);
    if (!contentRoots.isEmpty()) {
      EdtTestUtil.runInEdtAndWait(() -> {
        for (String root : contentRoots) {
          VirtualFile f = projectRoot.findFileByRelativePath(root);
          if (f != null) {
            myRootModel.addContentEntry(f);
          }
        }
      });
    }
  }

  void createProjectStructure(@NotNull Project project, @NotNull Collection<String> paths) {
    for (String path : paths) {
      cd(project.getBaseDir().getPath());
      File f = new File(project.getBaseDir().getPath(), path);
      f.mkdirs();
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
    }
    projectRoot.refresh(false, true);
  }

  private void createDirs(@NotNull Collection<String> mockRoots) throws IOException {
    File baseDir;
    if (mockRoots.isEmpty()) {
      return;
    }

    baseDir = VfsUtilCore.virtualToIoFile(myProject.getBaseDir());
    int maxDepth = findMaxDepthAboveProject(mockRoots);
    File projectDir = createChild(baseDir, maxDepth - 1);
    cd(projectDir.getPath());
    for (String path : mockRoots) {
      File mockDir = new File(new File(projectDir, path), DOT_MOCK);
      mockDir.mkdirs();
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(mockDir);
    }
  }

  @NotNull
  private static File createChild(@NotNull File base, int depth) throws IOException {
    File dir = base;
    if (depth < 0) {
      return dir;
    }
    for (int i = 0; i < depth; ++i) {
      dir = FileUtil.createTempDirectory(dir, "grdt", null);
    }
    return dir;
  }

  // Assuming that there are no ".." inside the path - only in the beginning
  static int findMaxDepthAboveProject(@NotNull Collection<String> paths) {
    int max = 0;
    for (String path : paths) {
      String[] splits = path.split("/");
      int count = 0;
      for (String split : splits) {
        if (split.equals("..")) {
          count++;
        }
      }
      if (count > max) {
        max = count;
      }
    }
    return max;
  }
}
