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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
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
import java.util.Arrays;
import java.util.Collection;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.mkdir;


/**
 * @author Nadya Zabrodina
 */

public class VcsRootDetectorTest extends VcsRootPlatformTest {

  public void testNoRootsInProject() throws IOException {
    doTest(new VcsRootConfiguration(), null);
  }

  public void testProjectUnderSingleMockRoot() throws IOException {
    doTest(new VcsRootConfiguration().mock("."), myProjectRoot, ".");
  }

  public void testProjectWithMockRootUnderIt() throws IOException {
    cd(myProjectRoot);
    mkdir("src");
    mkdir(".idea");
    doTest(new VcsRootConfiguration().mock("community"), myProjectRoot, "community");
  }

  public void testProjectWithAllSubdirsUnderMockRootShouldStillBeNotFullyControlled() throws IOException {
    String[] dirNames = {".idea", "src", "community"};
    doTest(new VcsRootConfiguration().mock(dirNames), myProjectRoot, dirNames);
  }

  public void testProjectUnderVcsAboveIt() throws IOException {
    String subdir = "insideRepo";
    cd(myRepository);
    mkdir(subdir);
    VirtualFile vfile = myRepository.findChild(subdir);
    doTest(new VcsRootConfiguration().mock(myRepository.getName()), vfile, myRepository.getName()
    );
  }

  public void testIDEAProject() throws IOException {
    String[] names = {"community", "contrib", "."};
    doTest(new VcsRootConfiguration().mock(names), myProjectRoot, names);
  }

  public void testOneAboveAndOneUnder() throws IOException {
    String[] names = {myRepository.getName() + "/community", "."};
    doTest(new VcsRootConfiguration().mock(names), myRepository, names);
  }

  public void testOneAboveAndOneForProjectShouldShowOnlyProjectRoot() throws IOException {
    String[] names = {myRepository.getName(), "."};
    doTest(new VcsRootConfiguration().mock(names), myRepository, myRepository.getName());
  }

  public void testOneAboveAndSeveralUnderProject() throws IOException {
    String[] names = {".", myRepository.getName() + "/community", myRepository.getName() + "/contrib"};
    doTest(new VcsRootConfiguration().mock(names), myRepository, names);
  }

  public void testMultipleAboveShouldBeDetectedAsOneAbove() throws IOException {
    String subdir = "insideRepo";
    cd(myRepository);
    mkdir(subdir);
    VirtualFile vfile = myRepository.findChild(subdir);
    doTest(new VcsRootConfiguration().mock(".", myRepository.getName()), vfile, myRepository.getName());
  }

  public void testUnrelatedRootShouldNotBeDetected() throws IOException {
    doTest(new VcsRootConfiguration().mock("another"), myRepository);
  }


  public void testLinkedSourceRootAloneShouldBeDetected() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock("linked_root")
        .contentRoots("linked_root");
    doTest(vcsRootConfiguration, myRepository, "linked_root");
  }

  public void testLinkedSourceRootAndProjectRootShouldBeDetected() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock(".", "linked_root")
        .contentRoots("linked_root");
    doTest(vcsRootConfiguration, myProjectRoot, ".", "linked_root");
  }

  public void testLinkedSourceBelowMockRoot() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().contentRoots("linked_root/src")
        .mock(".", "linked_root");
    doTest(vcsRootConfiguration, myProjectRoot, ".", "linked_root");
  }

  // This is a test of performance optimization via limitation: don't scan deep though the whole VFS, i.e. don't detect deep roots
  public void testDontScanDeeperThan2LevelsBelowAContentRoot() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().mock("community", "content_root/lev1/lev2", "content_root2/lev1/lev2/lev3")
        .contentRoots("content_root");
    doTest(vcsRootConfiguration,
           myProjectRoot, "community", "content_root/lev1/lev2");
  }

  void assertRoots(@NotNull Collection<String> expectedRelativePaths, @NotNull Collection<String> actual) {
    VcsTestUtil.assertEqualCollections(actual, toAbsolute(expectedRelativePaths, myProject));
  }

  @NotNull
  public static Collection<String> toAbsolute(@NotNull Collection<String> relPaths, @NotNull final Project project) {
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
  private Collection<VcsRoot> detect(@Nullable VirtualFile startDir) {
    return ServiceManager.getService(myProject, VcsRootDetector.class).detect(startDir);
  }

  public void doTest(@NotNull VcsRootConfiguration vcsRootConfiguration,
                     @Nullable VirtualFile startDir,
                     @NotNull String... expectedPaths)
    throws IOException {
    initProject(vcsRootConfiguration);
    if (startDir != null) {
      startDir.refresh(false, true);
    }
    Collection<VcsRoot> vcsRoots = detect(startDir);
    assertRoots(Arrays.asList(expectedPaths), getPaths(
      ContainerUtil.filter(vcsRoots, new Condition<VcsRoot>() {
        @Override
        public boolean value(VcsRoot root) {
          assert root.getVcs() != null;
          return root.getVcs().getKeyInstanceMethod().equals(myVcs.getKeyInstanceMethod());
        }
      })
    ));
  }
}
