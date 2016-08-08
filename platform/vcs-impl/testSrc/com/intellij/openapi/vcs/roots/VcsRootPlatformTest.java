/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.mkdir;

public abstract class VcsRootPlatformTest extends UsefulTestCase {

  public static final String DOT_MOCK = ".mock";

  private VcsRootChecker myExtension;
  @NotNull protected ProjectLevelVcsManagerImpl myVcsManager;
  @NotNull protected MockAbstractVcs myVcs;
  @NotNull protected String myVcsName;
  protected Project myProject;
  protected VirtualFile myProjectRoot;
  protected VirtualFile myRepository;
  public static final String myRepositoryFolderName = "repository";
  private RootModelImpl myRootModel;
  protected static final Collection<File> myFilesToDelete = new HashSet<>();
  protected IdeaProjectTestFixture myProjectFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName(true)).getFixture();
    myProjectFixture.setUp();

    myProject = myProjectFixture.getProject();
    myProjectRoot = myProject.getBaseDir();
    cd(myProjectRoot);
    Module module = doCreateRealModuleIn("foo", myProject, EmptyModuleType
      .getInstance());
    myRootModel = ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).getRootModel();
    mkdir(myRepositoryFolderName);
    myProjectRoot.refresh(false, true);
    myRepository = myProjectRoot.findChild(myRepositoryFolderName);
    myVcs = new MockAbstractVcs(myProject);
    myVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    ExtensionPoint<VcsRootChecker> point = getExtensionPoint();
    myExtension = new VcsRootChecker() {
      @Override
      public VcsKey getSupportedVcs() {
        return myVcs.getKeyInstanceMethod();
      }

      @Override
      public boolean isRoot(@NotNull String path) {
        return new File(path, DOT_MOCK).exists();
      }

      @Override
      public boolean isVcsDir(@Nullable String path) {
        return path != null && path.toLowerCase().endsWith(DOT_MOCK);
      }
    };
    point.registerExtension(myExtension);
    myVcsManager.registerVcs(myVcs);
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
      myVcsManager.unregisterVcs(myVcs);
      for (File file : myFilesToDelete) {
        delete(file);
      }
      myProjectFixture.tearDown();
    }
    finally {
      super.tearDown();
    }
  }

  private static void delete(File file) {
    boolean b = FileUtil.delete(file);
    if (!b && file.exists()) {
      fail("Can't delete " + file.getAbsolutePath());
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
    createDirs(vcsRootConfiguration.getMockRoots());
    Collection<String> contentRoots = vcsRootConfiguration.getContentRoots();
    createProjectStructure(myProject, contentRoots);
    if (!contentRoots.isEmpty()) {
      for (String root : contentRoots) {
        myProjectRoot.refresh(false, true);
        VirtualFile f = myProjectRoot.findFileByRelativePath(root);
        if (f != null) {
          myRootModel.addContentEntry(f);
        }
      }
    }
  }

  static void createProjectStructure(@NotNull Project project, @NotNull Collection<String> paths) {
    for (String path : paths) {
      cd(project.getBaseDir().getPath());
      File f = new File(project.getBaseDir().getPath(), path);
      f.mkdirs();
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
    }
  }

  @NotNull
  public static Module doCreateRealModuleIn(@NotNull String moduleName,
                                            @NotNull final Project project,
                                            @NotNull final ModuleType moduleType) {
    final VirtualFile baseDir = project.getBaseDir();
    assertNotNull(baseDir);
    final File moduleFile = new File(baseDir.getPath().replace('/', File.separatorChar),
                                     moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    FileUtil.createIfDoesntExist(moduleFile);
    myFilesToDelete.add(moduleFile);
    return new WriteAction<Module>() {
      @Override
      protected void run(@NotNull Result<Module> result) throws Throwable {
        final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleFile);
        assert virtualFile != null;
        Module module = ModuleManager.getInstance(project).newModule(virtualFile.getPath(), moduleType.getId());
        module.getModuleFile();
        result.setResult(module);
      }
    }.execute().getResultObject();
  }

  /**
   * @return path to the project
   */
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
      File file = new File(projectDir, path);
      file.mkdirs();
      File mockDir = new File(file, DOT_MOCK);
      mockDir.mkdirs();
      myFilesToDelete.add(mockDir);
      mockDir.deleteOnExit();
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
