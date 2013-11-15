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

import com.intellij.dvcs.test.MockVirtualFile;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.roots.VcsRootDetectInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.test.GitLightTest;
import git4idea.test.TestNotificator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.*;

import static junit.framework.Assert.*;

/**
 * @author Nadya Zabrodina
 */
public class GitIntegrationEnablerTest extends GitLightTest {

  public AbstractVcs myVcs;

  @Override
  @Before
  public void setUp() {
    super.setUp();
    myVcs = myPlatformFacade.getVcs(myProject);
  }

  @After
  public void tearDown() {
    super.tearDown();
  }

  @Test
  public void oneRootForTheWholeProjectThenJustAddVcsrRoot() {
    Map<String, List<String>> map = new HashMap<String, List<String>>();
    map.put("git_init", Collections.<String>emptyList());
    doTest(given(Arrays.asList("."), true, false),
           map, null);
  }

  @Test
  public void noGitRootsThenInitAndNotify() {
    Map<String, List<String>> map = new HashMap<String, List<String>>();
    map.put("git_init", Arrays.asList("."));
    map.put("vcs_roots", VcsTestUtil.toAbsolute(Arrays.asList("."), myProject));


    doTest(given(Collections.<String>emptyList(), false, false),
           map, notification("Created Git repository in " + myProjectRoot));
  }

  @Test
  public void belowGitNoInsideThenNotify() {
    Map<String, List<String>> map = new HashMap<String, List<String>>();
    map.put("git_init", Collections.<String>emptyList());

    doTest(given(Arrays.asList(".."), true, true),
           map, notification("Added Git root: " + myTestRoot));
  }

  @Test
  public void gitForProjectSomeInsideThenNotify() {
    Map<String, List<String>> map = new HashMap<String, List<String>>();
    map.put("git_init", Collections.<String>emptyList());

    doTest(given(Arrays.asList(".", "community"), true, false),
           map, notification("Added Git roots: " + myProjectRoot + ", " + getPresentationForRoot("community")));
  }

  @Test
  public void belowGitSomeInsideThenNotify() {
    Map<String, List<String>> map = new HashMap<String, List<String>>();
    map.put("git_init", Collections.<String>emptyList());

    doTest(given(Arrays.asList("..", "community"), true, true),
           map, notification("Added Git roots: " + myTestRoot + ", " + getPresentationForRoot("community")));
  }

  @Test
  public void notUnderGitSomeInsideThenNotify() {
    Map<String, List<String>> map = new HashMap<String, List<String>>();
    map.put("git_init", Collections.<String>emptyList());

    doTest(given(Arrays.asList("community", "contrib"), false, false),
           map, notification(
      "Added Git roots: " + getPresentationForRoot("community") + ", " + getPresentationForRoot("contrib")));
  }

  private void doTest(@NotNull VcsRootDetectInfo detectInfo, @NotNull Map<String, List<String>> map, @Nullable Notification notification) {

    //default
    if (map.get("vcs_roots") == null) {
      map.put("vcs_roots", ContainerUtil.map(detectInfo.getRoots(), new Function<VcsRoot, String>() {

        @Override
        public String fun(VcsRoot root) {
          assert root.getPath() != null;
          return root.getPath().getPath();
        }
      }));
    }

    new GitIntegrationEnabler(myProject, myGit, myPlatformFacade).enable(detectInfo);

    assertVcsRoots(map.get("vcs_roots"));
    assertGitInit(map.get("git_init"));
    assertNotificationShown(notification);
  }

  void assertGitInit(@NotNull Collection<String> roots) {
    for (String root : roots) {
      File rootFile = new File(myProjectRoot, root);
      assertTrue(new File(rootFile.getPath(), GitUtil.DOT_GIT).exists());
    }
  }

  void assertVcsRoots(@NotNull Collection<String> expectedVcsRoots) {
    VirtualFile[] actualRoots = myPlatformFacade.getVcsManager(myProject).getRootsUnderVcs(myPlatformFacade.getVcs(myProject));
    VcsTestUtil.assertEqualCollections(expectedVcsRoots, getPaths(actualRoots));
  }

  VcsRootDetectInfo given(@NotNull Collection<String> roots, boolean full, boolean below) {
    return new VcsRootDetectInfo(ContainerUtil.map(roots, new Function<String, VcsRoot>() {

      @Override
      public VcsRoot fun(String s) {
        return new VcsRoot(myVcs, new MockVirtualFile(VcsTestUtil.toAbsolute(s, myProject)));
      }
    }), full, below);
  }

  Notification notification(String content) {
    return new Notification("Test", "", content, NotificationType.INFORMATION);
  }

  void assertNotificationShown(@Nullable Notification expected) {
    if (expected != null) {
      Notification actualNotification = ((TestNotificator)myPlatformFacade.getNotificator(myProject)).getLastNotification();
      assertNotNull("No notification was shown", actualNotification);
      assertEquals("Notification has wrong title", expected.getTitle(), actualNotification.getTitle());
      assertEquals("Notification has wrong type", expected.getType(), actualNotification.getType());
      assertEquals("Notification has wrong content", adjustTestContent(expected.getContent()), actualNotification.getContent());
    }
  }

  // we allow more spaces and line breaks in tests to make them more readable.
  // After all, notifications display html, so all line breaks and extra spaces are ignored.
  String adjustTestContent(@NotNull String s) {
    StringBuilder res = new StringBuilder();
    String[] splits = s.split("\n");
    for (String split : splits) {
      res.append(split.trim());
    }

    return res.toString();
  }

  @NotNull
  public static Collection<String> getPaths(@NotNull VirtualFile[] virtualFiles) {
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
