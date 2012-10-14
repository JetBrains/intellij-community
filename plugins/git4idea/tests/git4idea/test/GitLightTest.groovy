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

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import git4idea.PlatformFacade
import git4idea.commands.Git
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

  public static final String USER_NAME = "John Doe";
  public static final String USER_EMAIL = "John.Doe@example.com";

  protected String myRootDir
  protected GitMockProject myProject
  protected PlatformFacade myPlatformFacade
  protected Git myGit

  @Before
  protected void setUp() {
    myRootDir = FileUtil.createTempDirectory("", "").getPath()
    myProject = new GitMockProject(myRootDir)
    myPlatformFacade = new GitTestPlatformFacade()
    myGit = new GitTestImpl()
  }

  @After
  public void tearDown() {
    FileUtil.delete(new File(myRootDir))
    Disposer.dispose(myProject)
  }

  public void setupUsername() {
    git("config user.name $USER_NAME")
    git("config user.email $USER_EMAIL")
  }

  public void initRepo(String repoRoot) {
    cd repoRoot
    git("init")
    setupUsername();
    touch("file.txt")
    git("add file.txt")
    git("commit -m initial")
  }

}
