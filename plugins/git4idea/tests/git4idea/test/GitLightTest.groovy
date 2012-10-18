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
import git4idea.PlatformFacade
import git4idea.commands.Git
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryImpl
import org.junit.After
import org.junit.Before

/**
 * <p>GitLightTest is a test that doesn't need to start the whole {@link com.intellij.openapi.application.Application} and Project.
 *    It substitutes everything with Mocks, and communicates with this mocked platform via {@link GitTestPlatformFacade}.</p>
 *
 * <p>However, GitLightTests tests are not entirely unit. They may use other components from the git4idea plugin, they operate on the
 *    real file system, and they call native Git to prepare test case and from the code which is being tested.</p>
 *
 * @author Kirill Likhodedov
 */
@Mixin(GitExecutor)
class GitLightTest {

  private static final String USER_NAME = "John Doe";
  private static final String USER_EMAIL = "John.Doe@example.com";

  /**
   * The file system root of test files.
   * Automatically deleted on {@link #tearDown()}.
   * Tests should create new files only inside this directory.
   */
  protected String myTestRoot

  /**
   * The file system root of the project. All project should locate inside this directory.
   */
  protected String myProjectRoot

  protected MockProject myProject
  protected PlatformFacade myPlatformFacade
  protected Git myGit

  @Before
  protected void setUp() {
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

  protected GitRepository createRepository(String rootDir) {
    initRepo(rootDir)

    // TODO this smells hacky
    // the constructor and notifyListeners() should probably be private
    // getPresentableUrl should probably be final, and we should have a better VirtualFile implementation for tests.
    GitRepository repository = new GitRepositoryImpl(new MockVirtualFile(rootDir), myPlatformFacade, myProject, myProject, true) {
      @Override
      protected void notifyListeners() {
      }

      @Override
      String getPresentableUrl() {
        return rootDir;
      }
    }

    registerRepository(repository)

    return repository
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

  private void registerRepository(GitRepositoryImpl repository) {
    ((GitTestRepositoryManager)myPlatformFacade.getRepositoryManager(myProject)).add(repository)
  }

  private void initRepo(String repoRoot) {
    cd repoRoot
    git("init")
    setupUsername();
    touch("file.txt")
    git("add file.txt")
    git("commit -m initial")
  }

  private void setupUsername() {
    git("config user.name $USER_NAME")
    git("config user.email $USER_EMAIL")
  }

}
