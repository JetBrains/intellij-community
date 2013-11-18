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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.mkdir;


/**
 * @author Nadya Zabrodina
 */
public class VcsRootDetectorTest extends VcsRootPlatformTest {

  public void testNoRootsInProject() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Collections.<String>emptyList());
    map.put("content_roots", Collections.<String>emptyList());
    doTest(map, null, Collections.<String>emptyList(), false, false);
  }

  public void testProjectUnderSingleMockRoot() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList("."));
    map.put("content_roots", Collections.<String>emptyList());
    doTest(map, myProjectRoot, Arrays.asList("."), true, false);
  }

  public void testProjectWithMockRootUnderIt() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList("community"));
    map.put("content_roots", Collections.<String>emptyList());
    cd(myProjectRoot);
    mkdir("src");
    mkdir(".idea");
    doTest(map, myProjectRoot, Arrays.asList("community"), false, false);
  }

  public void testProjectWithAllSubdirsUnderMockRootShouldStillBeNotFullyControlled() throws IOException {
    String[] dirNames = {".idea", "src", "community"};
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(dirNames));
    map.put("content_roots", Collections.<String>emptyList());
    doTest(map, myProjectRoot, Arrays.asList(dirNames), false, false);
  }

  public void testProjectUnderVcsAboveIt() throws IOException {
    String subdir = "insideRepo";
    cd(myRepository);
    mkdir(subdir);
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(myRepository.getName()));
    map.put("content_roots", Collections.<String>emptyList());
    VirtualFile vfile = myRepository.findChild(subdir);
    doTest(map, vfile, Arrays.asList(myRepository.getName()),
           true, true);
  }

  public void testIDEAProject() throws IOException {
    String[] names = {"community", "contrib", "."};
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(names));
    map.put("content_roots", Collections.<String>emptyList());
    doTest(map, myProjectRoot, Arrays.asList(names), true, false);
  }

  public void testOneAboveAndOneUnder() throws IOException {
    String[] names = {myRepository.getName() + "/community", "."};
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(names));
    map.put("content_roots", Collections.<String>emptyList());
    doTest(map, myRepository, Arrays.asList(names), true, true);
  }

  public void testOneAboveAndOneForProjectShouldShowOnlyProjectRoot() throws IOException {
    String[] names = {myRepository.getName(), "."};
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(names));
    map.put("content_roots", Collections.<String>emptyList());
    doTest(map, myRepository, Arrays.asList(myRepository.getName()), true, false);
  }

  public void testOneAboveAndSeveralUnderProject() throws IOException {
    String[] names = {".", myRepository.getName() + "/community", myRepository.getName() + "/contrib"};
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(names));
    map.put("content_roots", Collections.<String>emptyList());
    doTest(map, myRepository, Arrays.asList(names), true, true);
  }

  public void testMultipleAboveShouldBeDetectedAsOneAbove() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(".", myRepository.getName()));
    map.put("content_roots", Collections.<String>emptyList());
    String subdir = "insideRepo";
    cd(myRepository);
    mkdir(subdir);
    VirtualFile vfile = myRepository.findChild(subdir);
    doTest(map, vfile, Arrays.asList(myRepository.getName()), true, true);
  }

  public void testUnrelatedRootShouldNotBeDetected() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList("another"));
    map.put("content_roots", Collections.<String>emptyList());
    doTest(map, myRepository, Collections.<String>emptyList(), false, false);
  }


  public void testLinkedSourceRootAloneShouldBeDetected() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList("linked_root"));
    map.put("content_roots", Arrays.asList("linked_root"));
    doTest(map, myRepository, Arrays.asList("linked_root"), false, false);
  }

  public void testLinkedSourceRootAndProjectRootShouldBeDetected() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(".", "linked_root"));
    map.put("content_roots", Arrays.asList("linked_root"));
    doTest(map, myProjectRoot, Arrays.asList(".", "linked_root"), true, false);
  }

  public void testLinkedSourceBelowMockRoot() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList(".", "linked_root"));
    map.put("content_roots", Arrays.asList("linked_root/src"));
    doTest(map, myProjectRoot, Arrays.asList(".", "linked_root"), true, false);
  }

  // This is a test of performance optimization via limitation: don't scan deep though the whole VFS, i.e. don't detect deep roots
  public void testDontScanDeeperThan2LevelsBelowAContentRoot() throws IOException {
    Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
    map.put("mock", Arrays.asList("community", "content_root/lev1/lev2", "content_root2/lev1/lev2/lev3"));
    map.put("content_roots", Arrays.asList("content_root"));
    doTest(map, myProjectRoot, Arrays.asList("community", "content_root/lev1/lev2"), false, false);
  }

  void assertRoots(Collection<String> expectedRelativePaths, Collection<String> actual) {
    VcsTestUtil.assertEqualCollections(actual, toAbsolute(expectedRelativePaths, myProject));
  }

  @NotNull
  public static Collection<String> toAbsolute(Collection<String> relPaths, final Project project) {
    return ContainerUtil.map(relPaths, new Function<String, String>() {
      @Override
      public String fun(String s) {
        try {
          return FileUtil.toSystemIndependentName(new File(project.getBaseDir().getPath(), s).getCanonicalPath());
        }
        catch (IOException e) {
          fail();
          e.printStackTrace();
          return null;
        }
      }
    });
  }

  @NotNull
  static Collection<String> getPaths(@NotNull Collection<VcsRoot> files) {
    return ContainerUtil.map(files, new Function<VcsRoot, String>() {
      @Override
      public String fun(VcsRoot root) {
        VirtualFile file = root.getPath();
        assert file != null;
        return FileUtil.toSystemIndependentName(file.getPath());
      }
    });
  }

  @NotNull
  private VcsRootDetectInfo detect(@Nullable VirtualFile startDir) {
    return new VcsRootDetector(myProject).detect(startDir);
  }

  public void doTest(@NotNull Map<String, Collection<String>> map,
                     @Nullable VirtualFile startDir,
                     @NotNull Collection<String> expectedPaths,
                     boolean expectedFull,
                     boolean expectedBelow)
    throws IOException {
    initProject(map.get("mock"), Collections.<String>emptyList(), map.get("content_roots"));

    VcsRootDetectInfo info = detect(startDir);
    assertRoots(expectedPaths, getPaths(info.getRoots()));
    assertEquals(expectedFull, info.totallyUnderVcs());
    assertEquals(expectedBelow, info.projectIsBelowVcs());
  }
}
