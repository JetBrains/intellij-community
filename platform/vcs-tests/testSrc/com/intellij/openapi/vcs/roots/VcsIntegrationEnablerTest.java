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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class VcsIntegrationEnablerTest extends VcsRootBaseTest {

  private VirtualFile myTestRoot;

  public void setUp() throws Exception {
    super.setUp();
    MutablePicoContainer picoContainer = (MutablePicoContainer)myProject.getPicoContainer();
    String vcsNotifierKey = VcsNotifier.class.getName();
    picoContainer.unregisterComponent(vcsNotifierKey);
    picoContainer.registerComponentImplementation(vcsNotifierKey, TestVcsNotifier.class);
    myTestRoot = projectRoot.getParent();
  }

  public void testOneRootForTheWholeProjectThenJustAddVcsRoot() {
    doTest(given("."), null, null);
  }

  public void testNoMockRootsThenInitAndNotify() {
    doTest(given(),
           notification("Created mock repository in " + projectRoot.getPresentableUrl()), ".", VcsTestUtil.toAbsolute(".", myProject));
  }

  public void testBelowMockNoInsideThenNotify() {
    doTest(given(".."),
           notification("Added mock root: " + myTestRoot.getPresentableUrl()));
  }

  public void testMockForProjectSomeInsideThenNotify() {
    doTest(given(".", "community"),
           notification("Added mock roots: " + projectRoot.getPresentableUrl() + ", " + getPresentationForRoot("community")));
  }

  public void testBelowMockSomeInsideThenNotify() {
    doTest(given("..", "community"),
           notification("Added mock roots: " + myTestRoot.getPresentableUrl() + ", " + getPresentationForRoot("community")));
  }

  public void testNotUnderMockSomeInsideThenNotify() {
    doTest(given("community", "contrib"),
           notification(
             "Added mock roots: " + getPresentationForRoot("community") + ", " + getPresentationForRoot("contrib"))
    );
  }

  private void doTest(@NotNull Collection<VcsRoot> vcsRoots,
                      @Nullable Notification notification

  ) {
    doTest(vcsRoots, notification, null);
  }

  private void doTest(@NotNull Collection<VcsRoot> vcsRoots,
                      @Nullable Notification notification,
                      @Nullable String mock_init,
                      @NotNull String... vcs_roots) {

    List<String> vcsRootsList = ContainerUtil.newArrayList(vcs_roots);
    //default
    if (vcsRootsList.isEmpty()) {
      vcsRootsList.addAll(ContainerUtil.map(vcsRoots, root -> {
        assert root.getPath() != null;
        return root.getPath().getPath();
      }));
    }
    new TestIntegrationEnabler(myVcs).enable(vcsRoots);
    assertVcsRoots(vcsRootsList);
    if (mock_init != null) {
      assertMockInit(mock_init);
    }
    VcsTestUtil.assertNotificationShown(myProject, notification);
  }

  void assertMockInit(@NotNull String root) {
    File rootFile = new File(projectRoot.getPath(), root);
    assertTrue(new File(rootFile.getPath(), DOT_MOCK).exists());
  }

  void assertVcsRoots(@NotNull Collection<String> expectedVcsRoots) {
    List<VirtualFile> actualRoots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcsWithoutFiltering(myVcs);
    VcsTestUtil.assertEqualCollections(expectedVcsRoots, ContainerUtil.map(actualRoots, VirtualFile::getPath));
  }

  private Collection<VcsRoot> given(@NotNull String... roots) {
    return ContainerUtil.map(roots, s -> {
      String path = VcsTestUtil.toAbsolute(s, myProject);
      LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      return new VcsRoot(myVcs, VcsUtil.getVirtualFile(path));
    });
  }

  Notification notification(String content) {
    return new Notification("Test", "", content, NotificationType.INFORMATION);
  }

  @NotNull
  private String getPresentationForRoot(@NotNull String root) {
    return FileUtil.toSystemDependentName(VcsTestUtil.toAbsolute(root, myProject));
  }

  private static class TestIntegrationEnabler extends VcsIntegrationEnabler {

    protected TestIntegrationEnabler(@NotNull MockAbstractVcs vcs) {
      super(vcs);
    }

    @Override
    protected boolean initOrNotifyError(@NotNull final VirtualFile projectDir) {
      File file = new File(projectDir.getPath(), DOT_MOCK);
      VcsNotifier.getInstance(myProject).notifySuccess("Created mock repository in " + projectDir.getPresentableUrl());
      return file.mkdir();
    }
  }
}
