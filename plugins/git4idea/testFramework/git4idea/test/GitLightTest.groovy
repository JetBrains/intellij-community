/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.test

import com.intellij.dvcs.test.MockProject
import com.intellij.dvcs.test.MockVirtualFile
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import git4idea.GitPlatformFacade
import git4idea.commands.Git
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryImpl
import org.jetbrains.annotations.NotNull
import org.junit.After
import org.junit.Before
/**
 * <p>GitLightTest is a test that doesn't need to start the whole {@link com.intellij.openapi.application.Application} and Project.
 *    It substitutes everything with Mocks, and communicates with this mocked platform via {@link com.intellij.dvcs.test.DvcsTestPlatformFacade}.</p>
 *
 * <p>However, GitLightTests tests are not entirely unit. They may use other components from the git4idea plugin, they operate on the
 *    real file system, and they call native Git to prepare test case and from the code which is being tested.</p>
 *
 * @author Kirill Likhodedov
 */
class GitLightTest extends GitExecutor {

  /**
   * The file system root of test files.
   * Automatically deleted on {@link #tearDown()}.
   * Tests should create new files only inside this directory.
   */
  public String myTestRoot

  /**
   * The file system root of the project. All project should locate inside this directory.
   */
  public String myProjectRoot

  public MockProject myProject
  public GitPlatformFacade myPlatformFacade
  public Git myGit

  @Before
  public void setUp() {
    myTestRoot = FileUtil.createTempDirectory("", "").getPath()
    cd myTestRoot
    myProjectRoot = mkdir ("project")
    myProject = new MockProject(myProjectRoot)
    myPlatformFacade = new GitTestPlatformFacade()
    myGit = new GitTestImpl()
  }

  @After
  protected void tearDown() {
    FileUtil.delete(new File(myTestRoot))
    Disposer.dispose(myProject)
  }

  public GitRepository createRepository(String rootDir) {
    GitTestUtil.initRepo(rootDir);

    // TODO this smells hacky
    // the constructor and notifyListeners() should probably be private
    // getPresentableUrl should probably be final, and we should have a better VirtualFile implementation for tests.
    GitRepository repository = new GitRepositoryImpl(new MockVirtualFile(rootDir), myPlatformFacade, myProject, myProject, true) {
      @NotNull
      @Override
      public String getPresentableUrl() {
        return rootDir;
      }

    };

    ((GitTestRepositoryManager)myPlatformFacade.getRepositoryManager(myProject)).add(repository);
    return repository;
  }

  /**
   * Clones the given source repository into a bare parent.git and adds the remote origin.
   */
  protected void prepareRemoteRepo(GitRepository source, String target = "parent.git", String targetName = "origin") {
    cd myTestRoot
    git("clone --bare $source $target")
    cd source
    git("remote add $targetName $myTestRoot/$target");
  }
}
