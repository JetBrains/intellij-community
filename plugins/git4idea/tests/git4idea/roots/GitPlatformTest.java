/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.roots;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import static com.intellij.dvcs.test.Executor.*;

/**
 * @author Nadya Zabrodina
 */
public abstract class GitPlatformTest extends UsefulTestCase {

  protected Project myProject;
  protected VirtualFile myProjectRoot;
  protected VirtualFile myRepository;
  public static final String myRepositoryFolderName = "repository";
  private RootModelImpl myRootModel;
  protected static final Collection<File> myFilesToDelete = new HashSet<File>();

  protected IdeaProjectTestFixture myProjectFixture;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  protected GitPlatformTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

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
    myRepository = myProjectRoot.findChild(myRepositoryFolderName);
  }

  @Override
  protected void tearDown() throws Exception {
    for (File file : myFilesToDelete) {
      delete(file);
    }
    myProjectFixture.tearDown();
    super.tearDown();
  }

  private static void delete(File file) {
    boolean b = FileUtil.delete(file);
    if (!b && file.exists()) {
      fail("Can't delete " + file.getAbsolutePath());
    }
  }

  /**
   * Creates the necessary temporary directories in the filesystem with empty ".git" directories for given roots.
   * And creates an instance of the project.
   *
   * @param gitRoots path to actual .git roots, relative to the project dir.
   */
  public void initProject(@NotNull Collection<String> gitRoots,
                          @NotNull Collection<String> projectStructure,
                          @NotNull Collection<String> contentRoots)
    throws IOException {
    createDirs(gitRoots);
    createProjectStructure(myProject, projectStructure);
    createProjectStructure(myProject, contentRoots);
    if (!contentRoots.isEmpty()) {
      for (String root : contentRoots) {
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
      protected void run(Result<Module> result) throws Throwable {
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
  private void createDirs(@NotNull Collection<String> gitRoots) throws IOException {
    File baseDir;
    if (gitRoots.isEmpty()) {
      return;
    }

    baseDir = VfsUtilCore.virtualToIoFile(myProject.getBaseDir());
    int maxDepth = findMaxDepthAboveProject(gitRoots);
    File projectDir = createChild(baseDir, maxDepth - 1);
    cd(projectDir.getPath());
    for (String path : gitRoots) {
      File file = new File(projectDir, path);
      file.mkdirs();
      File gitDir = new File(file, ".git");
      gitDir.mkdirs();
      myFilesToDelete.add(gitDir);
      gitDir.deleteOnExit();
      cd(gitDir.getPath());
      touch("HEAD", "ref: refs/heads/master");
      File head = new File(gitDir, "HEAD");
      myFilesToDelete.add(head);
      touch("config", "");
      File config = new File(gitDir, "config");
      myFilesToDelete.add(config);
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
