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
package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsRootError;
import com.intellij.openapi.vcs.VcsTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;


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
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Collections.<String>emptyList());
    map.put("roots", Collections.<String>emptyList());
    map.put("content_roots", Collections.<String>emptyList());
    doTest(map, Collections.<String, Collection<String>>emptyMap());
  }

  public void testSameOneRootInBothThenNoErrors() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList("."));
    map.put("roots", Arrays.asList("."));
    map.put("content_roots", Collections.<String>emptyList());
    doTest(map, Collections.<String, Collection<String>>emptyMap());
  }

  public void testSameTwoRootsInBothThenNoErrors() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(".", "community"));
    map.put("roots", Arrays.asList(".", "community"));
    map.put("content_roots", Collections.<String>emptyList());
    doTest(map, Collections.<String, Collection<String>>emptyMap());
  }

  public void testOneMockRootNoVCSRootsThenError() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList("."));
    map.put("roots", Collections.<String>emptyList());
    map.put("content_roots", Collections.<String>emptyList());
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("unreg", Arrays.asList("."));
    doTest(map, errorsMap);
  }

  public void testOneVCSRootNoMockRootsThenError() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("roots", Arrays.asList("."));
    map.put("mock", Collections.<String>emptyList());
    map.put("content_roots", Collections.<String>emptyList());
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("extra", Arrays.asList("."));
    doTest(map, errorsMap);
  }

  public void testOneRootButDifferentThenTwoErrors() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList("."));
    map.put("roots", Arrays.asList("community"));
    map.put("content_roots", Collections.<String>emptyList());
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("extra", Arrays.asList("community"));
    errorsMap.put("unreg", Arrays.asList("."));
    doTest(map, errorsMap);
  }

  public void testTwoRootsOneMatchingOneDifferentThenTwoErrors() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(".", "community"));
    map.put("roots", Arrays.asList(".", "contrib"));
    map.put("content_roots", Collections.<String>emptyList());
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("extra", Arrays.asList("contrib"));
    errorsMap.put("unreg", Arrays.asList("community"));
    doTest(map, errorsMap);
  }

  public void testTwoRootsInMockRootOneMatchingInVCSThenError() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(".", "community"));
    map.put("roots", Arrays.asList("."));
    map.put("content_roots", Collections.<String>emptyList());
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("unreg", Arrays.asList("community"));
    doTest(map, errorsMap);
  }

  public void testTwoRootsBothNotMatchingThenFourErrors() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(".", "community"));
    map.put("roots", Arrays.asList("another", "contrib"));
    map.put("content_roots", Collections.<String>emptyList());
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("extra", Arrays.asList("contrib", "another"));
    errorsMap.put("unreg", Arrays.asList("community", "."));
    doTest(map, errorsMap);
  }

  public void testProjectRootNoMockRootsThenErrorAboutExtraRoot() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Collections.<String>emptyList());
    map.put("roots", Arrays.asList(PROJECT));
    map.put("content_roots", Collections.<String>emptyList());
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("extra", Arrays.asList(PROJECT));
    doTest(map, errorsMap);
  }

  public void testProjectRootFullUnderMockRootThenCorrect() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList("."));
    map.put("roots", Arrays.asList(".", PROJECT));
    map.put("content_roots", Collections.<String>emptyList());
    doTest(map, Collections.<String, Collection<String>>emptyMap());
  }

  public void testProjectRootMockRootForAContentRootBelowProjectThenError() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList("content_root"));
    map.put("roots", Arrays.asList(PROJECT));
    map.put("content_roots", Arrays.asList("content_root"));
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("unreg", Arrays.asList("content_root"));
    errorsMap.put("extra", Arrays.asList(PROJECT));
    doTest(map, errorsMap);
  }

  public void testProjectRootMockRootBelowProjectFolderNotInAContentRootThenUnregisteredRootError() throws IOException {
    // this is to be fixed: auto-detection of MockRoot repositories in subfolders for the <Project> mapping
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList("community"));
    map.put("roots", Arrays.asList(PROJECT));
    map.put("content_roots", Arrays.asList("."));
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("unreg", Arrays.asList("community"));
    errorsMap.put("extra", Arrays.asList(PROJECT));
    doTest(map, errorsMap);
  }

  public void testProjectRootMockRootForFullProjectContentRootLinkedSourceFolderBelowProjectThenErrors() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(".", "content_root", "../linked_source_root", "folder"));
    map.put("roots", Arrays.asList(PROJECT));
    map.put("content_roots", Arrays.asList(".", "content_root", "../linked_source_root"));
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("unreg", Arrays.asList("content_root", "../linked_source_root", "folder"));
    doTest(map, errorsMap);
  }

  public void testProjectRootForFolderMockRootForFullProjectContentRootLinkedSourceFolderBelowProjectThenErrors() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(".", "content_root", "../linked_source_root", "folder"));
    map.put("roots", Arrays.asList(PROJECT, "folder"));
    map.put("content_roots", Arrays.asList(".", "content_root", "../linked_source_root"));
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("unreg", Arrays.asList("content_root", "../linked_source_root"));
    doTest(map, errorsMap);
  }

  public void testProjectRootMockRootLikeInIDEAProjectThenError() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(".", "community", "contrib"));
    map.put("roots", Arrays.asList(PROJECT));
    map.put("content_roots", Arrays.asList(".", "community", "contrib"));
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("unreg", Arrays.asList("community", "contrib"));
    doTest(map, errorsMap);
  }

  public void testRealMockRootRootDeeperThanThreeLevelsShouldBeDetected() throws IOException {

    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(".", "community", "contrib", "community/level1/level2/level3"));
    map.put("roots", Arrays.asList(PROJECT, "community/level1/level2/level3"));
    map.put("content_roots", Arrays.asList(".", "community", "contrib"));
    Map<String, Collection<String>> errorsMap = new HashMap<String, Collection<String>>();
    errorsMap.put("unreg", Arrays.asList("community", "contrib"));
    doTest(map, errorsMap);
  }

  private void doTest(@NotNull Map<String, Collection<String>> map, @NotNull Map<String, Collection<String>> errors) throws IOException {
    initProject(map.get("mock"), Collections.<String>emptyList(), map.get("content_roots"));
    addVcsRoots(map.get("roots"));

    Collection<VcsRootError> expected = new ArrayList<VcsRootError>();
    Collection<String> unregPaths = errors.get("unreg");
    Collection<String> extraPaths = errors.get("extra");
    if (unregPaths != null) {
      expected.addAll(unregAll(unregPaths));
    }
    if (extraPaths != null) {
      expected.addAll(extraAll(extraPaths));
    }
    Collection<VcsRootError> actual = new VcsRootErrorsFinder(myProject).find();
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
    Collection<VcsRootError> unregRoots = new ArrayList<VcsRootError>();
    for (String path : paths) {
      unregRoots.add(unreg(path));
    }
    return unregRoots;
  }

  @NotNull
  Collection<VcsRootError> extraAll(@NotNull Collection<String> paths) {
    Collection<VcsRootError> extraRoots = new ArrayList<VcsRootError>();
    for (String path : paths) {
      extraRoots.add(extra(path));
    }
    return extraRoots;
  }

  @NotNull
  VcsRootError unreg(@NotNull String path) {
    return new VcsRootError(VcsRootError.Type.UNREGISTERED_ROOT, VcsTestUtil.toAbsolute(path, myProject), myVcsName);
  }

  @NotNull
  VcsRootError extra(@NotNull String path) {
    return new VcsRootError(VcsRootError.Type.EXTRA_MAPPING, PROJECT.equals(path) ? PROJECT : VcsTestUtil.toAbsolute(path, myProject),
                            myVcsName);
  }
}
