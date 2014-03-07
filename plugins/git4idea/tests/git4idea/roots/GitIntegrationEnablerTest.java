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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.test.GitTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;


public class GitIntegrationEnablerTest extends UsefulTestCase {

  public Project myProject;
  protected VirtualFile myProjectRoot;
  protected VirtualFile myTestRoot;
  public GitVcs myVcs;
  public Git myGit;


  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public GitIntegrationEnablerTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  public void setUp() throws Exception {
    super.setUp();
    IdeaProjectTestFixture projectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName(true)).getFixture();
    projectFixture.setUp();
    myProject = projectFixture.getProject();
    myVcs = GitVcs.getInstance(myProject);
    myProjectRoot = myProject.getBaseDir();
    myTestRoot = myProjectRoot.getParent();
    myGit = ServiceManager.getService(myProject, Git.class);
  }

  public void testOneRootForTheWholeProjectThenJustAddVcsRoot() {
    doTest(given("."), null, null);
  }

  public void testNoGitRootsThenInitAndNotify() {
    doTest(given(),
           notification("Created Git repository in " + myProjectRoot.getPresentableUrl()), ".", VcsTestUtil.toAbsolute(".", myProject));
  }

  public void testBelowGitNoInsideThenNotify() {
    doTest(given(".."),
           notification("Added Git root: " + myTestRoot.getPresentableUrl()));
  }

  public void testGitForProjectSomeInsideThenNotify() {
    doTest(given(".", "community"),
           notification("Added Git roots: " + myProjectRoot.getPresentableUrl() + ", " + getPresentationForRoot("community")));
  }

  public void testBelowGitSomeInsideThenNotify() {
    doTest(given("..", "community"),
           notification("Added Git roots: " + myTestRoot.getPresentableUrl() + ", " + getPresentationForRoot("community")));
  }

  public void testNotUnderGitSomeInsideThenNotify() {
    doTest(given("community", "contrib"),
           notification(
             "Added Git roots: " + getPresentationForRoot("community") + ", " + getPresentationForRoot("contrib"))
    );
  }

  private void doTest(@NotNull Collection<VcsRoot> vcsRoots,
                      @Nullable Notification notification

  ) {
    doTest(vcsRoots, notification, null);
  }

  private void doTest(@NotNull Collection<VcsRoot> vcsRoots,
                      @Nullable Notification notification,
                      @Nullable String git_init,
                      @NotNull String... vcs_roots) {

    List<String> vcsRootsList = ContainerUtil.newArrayList(vcs_roots);
    //default
    if (vcsRootsList.isEmpty()) {
      vcsRootsList.addAll(ContainerUtil.map(vcsRoots, new Function<VcsRoot, String>() {

        @Override
        public String fun(VcsRoot root) {
          assert root.getPath() != null;
          return root.getPath().getPath();
        }
      }));
    }
    new GitIntegrationEnabler(myVcs, myGit).enable(vcsRoots);
    assertVcsRoots(vcsRootsList);
    if (git_init != null) {
      assertGitInit(git_init);
    }
    GitTestUtil.assertNotificationShown(myProject, notification);
  }

  void assertGitInit(@NotNull String root) {
    File rootFile = new File(myProjectRoot.getPath(), root);
    assertTrue(new File(rootFile.getPath(), GitUtil.DOT_GIT).exists());
  }

  void assertVcsRoots(@NotNull Collection<String> expectedVcsRoots) {
    List<VirtualFile> actualRoots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcsWithoutFiltering(myVcs);
    VcsTestUtil.assertEqualCollections(expectedVcsRoots, getPaths(actualRoots));
  }

  private Collection<VcsRoot> given(@NotNull String... roots) {
    return ContainerUtil.map(roots, new Function<String, VcsRoot>() {

      @Override
      public VcsRoot fun(String s) {
        return new VcsRoot(myVcs, VcsUtil.getVirtualFile(VcsTestUtil.toAbsolute(s, myProject)));
      }
    });
  }

  Notification notification(String content) {
    return new Notification("Test", "", content, NotificationType.INFORMATION);
  }

  @NotNull
  public static Collection<String> getPaths(@NotNull List<VirtualFile> virtualFiles) {
    return ContainerUtil.map(virtualFiles, new Function<VirtualFile, String>() {

      @Override
      public String fun(VirtualFile virtualFile) {
        return virtualFile.getPath();
      }
    });
  }

  @NotNull
  private String getPresentationForRoot(@NotNull String root) {
    return FileUtil.toSystemDependentName(VcsTestUtil.toAbsolute(root, myProject));
  }
}
