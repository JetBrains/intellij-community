/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.test;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.TestVcsNotifier;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import git4idea.DialogManager;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import static git4idea.test.GitExecutor.*;

public abstract class GitPlatformTest extends UsefulTestCase {

  @NotNull protected Project myProject;
  @NotNull protected VirtualFile myProjectRoot;
  @NotNull protected GitRepositoryManager myGitRepositoryManager;
  @NotNull protected GitVcsSettings myGitSettings;
  @NotNull protected GitPlatformFacade myPlatformFacade;
  @NotNull protected Git myGit;

  @NotNull protected TestDialogManager myDialogManager;
  @NotNull protected TestVcsNotifier myVcsNotifier;

  @NotNull private IdeaProjectTestFixture myProjectFixture;

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors", "UnusedDeclaration"})
  protected GitPlatformTest() {
    PlatformTestCase.initPlatformLangPrefix();
    GitTestUtil.setDefaultBuiltInServerPort();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    try {
      myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName(true)).getFixture();
      myProjectFixture.setUp();
    }
    catch (Exception e) {
      super.tearDown();
      throw e;
    }

    myProject = myProjectFixture.getProject();
    myProjectRoot = myProject.getBaseDir();

    myGitSettings = GitVcsSettings.getInstance(myProject);
    myGitSettings.getAppSettings().setPathToGit(GIT_EXECUTABLE);

    myDialogManager = (TestDialogManager)ServiceManager.getService(DialogManager.class);
    myVcsNotifier = (TestVcsNotifier)ServiceManager.getService(myProject, VcsNotifier.class);

    myGitRepositoryManager = GitUtil.getRepositoryManager(myProject);
    myPlatformFacade = ServiceManager.getService(myProject, GitPlatformFacade.class);
    myGit = ServiceManager.getService(myProject, Git.class);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myDialogManager.cleanup();
      myVcsNotifier.cleanup();
      myProjectFixture.tearDown();
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  protected GitRepository createRepository(@NotNull String rootDir) {
    return GitTestUtil.createRepository(myProject, rootDir);
  }

  /**
   * Clones the given source repository into a bare parent.git and adds the remote origin.
   */
  protected void prepareRemoteRepo(@NotNull GitRepository source) {
    final String target = "parent.git";
    final String targetName = "origin";
    cd(myProjectRoot);
    git("clone --bare '%s' %s", source.getRoot().getPath(), target);
    cd(source);
    git("remote add %s '%s'", targetName, myProjectRoot + "/" + target);
  }

}
