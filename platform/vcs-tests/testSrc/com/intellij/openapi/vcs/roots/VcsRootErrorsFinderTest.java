/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsRootError;
import com.intellij.openapi.vcs.VcsRootErrorImpl;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;


/**
 * @author Nadya Zabrodina
 */
public class VcsRootErrorsFinderTest extends VcsRootPlatformTest {

  static final String PROJECT = VcsDirectoryMapping.PROJECT_CONSTANT;

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testNoRootsThenNoErrors() throws IOException {
    doTest(new VcsRootConfiguration());
  }

  public void testSameOneRootInBothThenNoErrors() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".")
        .roots(".");
    doTest(vcsRootConfiguration);
  }

  public void testSameTwoRootsInBothThenNoErrors() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".", "community")
        .roots(".", "community");
    doTest(vcsRootConfiguration);
  }

  public void testOneMockRootNoVCSRootsThenError() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".")
        .unregErrors(".");
    doTest(vcsRootConfiguration);
  }

  public void testOneVCSRootNoMockRootsThenError() throws IOException {

    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().roots(".")
        .extraErrors(".");
    doTest(vcsRootConfiguration);
  }


  public void testOneRootButDifferentThenTwoErrors() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".")
        .roots("community")
        .unregErrors(".").extraErrors("community");
    doTest(vcsRootConfiguration);
  }

  public void testTwoRootsOneMatchingOneDifferentThenTwoErrors() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".", "community")
        .roots(".", "contrib")
        .unregErrors("community").extraErrors("contrib");
    doTest(vcsRootConfiguration);
  }

  public void testTwoRootsInMockRootOneMatchingInVCSThenError() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".", "community")
        .roots(".")
        .unregErrors("community");
    doTest(vcsRootConfiguration);
  }

  public void testTwoRootsBothNotMatchingThenFourErrors() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".", "community")
        .roots("another", "contrib")
        .unregErrors("community", ".").extraErrors("contrib", "another");
    doTest(vcsRootConfiguration);
  }

  public void testProjectRootNoMockRootsThenErrorAboutExtraRoot() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration()
        .roots(PROJECT)
        .extraErrors(PROJECT);
    doTest(vcsRootConfiguration);
  }

  public void testProjectRootFullUnderMockRootThenCorrect() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".")
        .roots(PROJECT);
    doTest(vcsRootConfiguration);
  }

  public void testProjectRootMockRootForAContentRootBelowProjectThenError() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock("content_root")
        .contentRoots("content_root").roots(PROJECT)
        .unregErrors("content_root").extraErrors(PROJECT);
    doTest(vcsRootConfiguration);
  }

  public void testProjectRootMockRootBelowProjectFolderNotInAContentRootThenUnregisteredRootError() throws IOException {
    // this is to be fixed: auto-detection of MockRoot repositories in subfolders for the <Project> mapping
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock("community")
        .contentRoots(".").roots(PROJECT)
        .unregErrors("community").extraErrors(PROJECT);
    doTest(vcsRootConfiguration);
  }

  public void testProjectRootMockRootForFullProjectContentRootLinkedSourceFolderBelowProjectThenErrors() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".", "content_root", "../linked_source_root", "folder")
        .roots(PROJECT)
        .contentRoots(".", "content_root", "../linked_source_root")
        .unregErrors("content_root", "../linked_source_root", "folder");
    doTest(vcsRootConfiguration);
  }

  public void testProjectRootForFolderMockRootForFullProjectContentRootLinkedSourceFolderBelowProjectThenErrors() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".", "content_root", "../linked_source_root", "folder")
        .roots(PROJECT, "folder")
        .contentRoots(".", "content_root", "../linked_source_root")
        .unregErrors("content_root", "../linked_source_root");
    doTest(vcsRootConfiguration);
  }

  public void testProjectRootMockRootLikeInIDEAProjectThenError() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".", "community", "contrib").roots(PROJECT)
        .contentRoots(".", "community", "contrib").unregErrors("community", "contrib");
    doTest(vcsRootConfiguration);
  }

  public void testRealMockRootRootDeeperThanThreeLevelsShouldBeDetected() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".", "community", "contrib", "community/level1/level2/level3")
        .contentRoots(".", "community", "contrib").roots(PROJECT, "community/level1/level2/level3")
        .unregErrors("community", "contrib");
    doTest(vcsRootConfiguration);
  }

  private void doTest(@NotNull VcsRootConfiguration vcsRootConfiguration) throws IOException {
    initProject(vcsRootConfiguration);
    addVcsRoots(vcsRootConfiguration.getRoots());

    Collection<VcsRootError> expected = new ArrayList<>();
    expected.addAll(unregAll(vcsRootConfiguration.getUnregErrors()));
    expected.addAll(extraAll(vcsRootConfiguration.getExtraErrors()));
    myProjectRoot.refresh(false, true);
    Collection<VcsRootError> actual = ContainerUtil.filter(new VcsRootErrorsFinder(myProject).find(), new Condition<VcsRootError>() {
      @Override
      public boolean value(VcsRootError error) {
        return error.getVcsKey().equals(myVcs.getKeyInstanceMethod());
      }
    });
    VcsTestUtil.assertEqualCollections(actual, expected);
  }

  void addVcsRoots(@NotNull Collection<String> relativeRoots) {
    for (String root : relativeRoots) {
      if (root.equals(PROJECT)) {
        myVcsManager.setDirectoryMapping("", myVcsName);
      }
      else {
        String absoluteRoot = VcsTestUtil.toAbsolute(root, myProject);
        myVcsManager.setDirectoryMapping(absoluteRoot, myVcsName);
      }
    }
  }

  @NotNull
  Collection<VcsRootError> unregAll(@NotNull Collection<String> paths) {
    Collection<VcsRootError> unregRoots = new ArrayList<>();
    for (String path : paths) {
      unregRoots.add(unreg(path));
    }
    return unregRoots;
  }

  @NotNull
  Collection<VcsRootError> extraAll(@NotNull Collection<String> paths) {
    Collection<VcsRootError> extraRoots = new ArrayList<>();
    for (String path : paths) {
      extraRoots.add(extra(path));
    }
    return extraRoots;
  }

  @NotNull
  VcsRootError unreg(@NotNull String path) {
    return new VcsRootErrorImpl(VcsRootError.Type.UNREGISTERED_ROOT, VcsTestUtil.toAbsolute(path, myProject), myVcsName);
  }

  @NotNull
  VcsRootError extra(@NotNull String path) {
    return new VcsRootErrorImpl(VcsRootError.Type.EXTRA_MAPPING, PROJECT.equals(path) ? PROJECT : VcsTestUtil.toAbsolute(path, myProject),
                                myVcsName);
  }
}
