// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.mkdir;
import static com.intellij.openapi.vcs.VcsTestUtil.assertEqualCollections;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class VcsRootDetectorTest extends VcsRootBaseTest {

  public void testNoRootsInProject() throws IOException {
    doTest(new VcsRootConfiguration(), null);
  }

  public void testProjectUnderSingleMockRoot() throws IOException {
    doTest(new VcsRootConfiguration().vcsRoots("."), projectRoot, ".");
  }

  public void testProjectWithMockRootUnderIt() throws IOException {
    cd(projectRoot);
    mkdir("src");
    mkdir(PathMacroUtil.DIRECTORY_STORE_NAME);
    doTest(new VcsRootConfiguration().vcsRoots("community"), projectRoot, "community");
  }

  public void testProjectWithAllSubdirsUnderMockRootShouldStillBeNotFullyControlled() throws IOException {
    String[] dirNames = {PathMacroUtil.DIRECTORY_STORE_NAME, "src", "community"};
    doTest(new VcsRootConfiguration().vcsRoots(dirNames), projectRoot, dirNames);
  }

  public void testProjectUnderVcsAboveIt() throws IOException {
    String subdir = "insideRepo";
    cd(myRepository);
    mkdir(subdir);
    VirtualFile vfile = myRepository.findChild(subdir);
    doTest(new VcsRootConfiguration().vcsRoots(myRepository.getName()), vfile, myRepository.getName()
    );
  }

  public void testIDEAProject() throws IOException {
    String[] names = {"community", "contrib", "."};
    doTest(new VcsRootConfiguration().vcsRoots(names), projectRoot, names);
  }

  public void testOneAboveAndOneUnder() throws IOException {
    String[] names = {myRepository.getName() + "/community", "."};
    doTest(new VcsRootConfiguration().vcsRoots(names), myRepository, names);
  }

  public void testOneAboveAndOneForProjectShouldShowOnlyProjectRoot() throws IOException {
    String[] names = {myRepository.getName(), "."};
    doTest(new VcsRootConfiguration().vcsRoots(names), myRepository, myRepository.getName());
  }

  public void testDontDetectAboveIfProjectIsIgnoredThere() throws IOException {
    myRootChecker.setIgnored(myRepository);
    assertTrue(new File(testRoot, DOT_MOCK).mkdir());
    doTest(new VcsRootConfiguration().vcsRoots(testRoot.getPath()), myRepository);
  }

  public void testOneAboveAndSeveralUnderProject() throws IOException {
    String[] names = {".", myRepository.getName() + "/community", myRepository.getName() + "/contrib"};
    doTest(new VcsRootConfiguration().vcsRoots(names), myRepository, names);
  }

  public void testMultipleAboveShouldBeDetectedAsOneAbove() throws IOException {
    String subdir = "insideRepo";
    cd(myRepository);
    mkdir(subdir);
    VirtualFile vfile = myRepository.findChild(subdir);
    doTest(new VcsRootConfiguration().vcsRoots(".", myRepository.getName()), vfile, myRepository.getName());
  }

  public void testUnrelatedRootShouldNotBeDetected() throws IOException {
    doTest(new VcsRootConfiguration().vcsRoots("another"), myRepository);
  }

  public void testLinkedSourceRootAloneShouldBeDetected() {
    String linkedRoot = "linked_root";
    File linkedRootDir = new File(testRoot, linkedRoot);
    assertTrue(new File(linkedRootDir, DOT_MOCK).mkdirs());
    myRootModel.addContentEntry(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(linkedRootDir));

    Collection<VcsRoot> roots = detect(projectRoot);

    assertEqualCollections(StreamEx.of(roots).map(it -> it.getPath().getPath()).toList(),
                           singletonList(toSystemIndependentName(linkedRootDir.getPath())));
  }

  public void testLinkedSourceRootAndProjectRootShouldBeDetected() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().vcsRoots(".", "linked_root")
        .contentRoots("linked_root");
    doTest(vcsRootConfiguration, projectRoot, ".", "linked_root");
  }

  public void testLinkedSourceBelowMockRoot() throws IOException {
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().contentRoots("linked_root/src")
        .vcsRoots(".", "linked_root");
    doTest(vcsRootConfiguration, projectRoot, ".", "linked_root");
  }

  // This is a test of performance optimization via limitation: don't scan deep though the whole VFS, i.e. don't detect deep roots
  public void testDontScanDeeperThan2LevelsBelowAContentRoot() throws IOException {
    Registry.get("vcs.root.detector.folder.depth").setValue(2, getTestRootDisposable());
    VcsRootConfiguration vcsRootConfiguration =
      new VcsRootConfiguration().vcsRoots("community", "content_root/lev1", "content_root2/lev1/lev2/lev3")
        .contentRoots("content_root");
    doTest(vcsRootConfiguration,
           projectRoot, "community", "content_root/lev1");
  }

  public void testDontScanExcludedDirs() throws IOException {
    VcsRootConfiguration vcsRootConfiguration = new VcsRootConfiguration()
        .contentRoots("community", "excluded")
        .vcsRoots("community", "excluded/lev1");
    setUp(vcsRootConfiguration, projectRoot);

    VirtualFile excludedFolder = projectRoot.findChild("excluded");
    assertNotNull(excludedFolder);
    markAsExcluded(excludedFolder);

    Collection<VcsRoot> vcsRoots = detect(projectRoot);
    assertRoots(singletonList("community"), getPaths(vcsRoots));
  }

  private void assertRoots(@NotNull Collection<String> expectedRelativePaths, @NotNull Collection<String> actual) {
    assertEqualCollections(actual, toAbsolute(expectedRelativePaths, myProject));
  }

  private void markAsExcluded(@NotNull VirtualFile dir) {
    ModuleRootModificationUtil.updateExcludedFolders(myRootModel.getModule(), dir, emptyList(), singletonList(dir.getUrl()));
  }

  @NotNull
  public static Collection<String> toAbsolute(@NotNull Collection<String> relPaths, @NotNull final Project project) {
    return ContainerUtil.map(relPaths, s -> {
      try {
        return toSystemIndependentName(new File(project.getBasePath(), s).getCanonicalPath());
      }
      catch (IOException e) {
        fail();
        e.printStackTrace();
        return null;
      }
    });
  }

  @NotNull
  static Collection<String> getPaths(@NotNull Collection<VcsRoot> files) {
    return ContainerUtil.map(files, root -> {
      VirtualFile file = root.getPath();
      assert file != null;
      return toSystemIndependentName(file.getPath());
    });
  }

  @NotNull
  private Collection<VcsRoot> detect(@Nullable VirtualFile startDir) {
    return ServiceManager.getService(myProject, VcsRootDetector.class).detect(startDir);
  }

  public void doTest(@NotNull VcsRootConfiguration vcsRootConfiguration,
                     @Nullable VirtualFile startDir,
                     @NotNull String... expectedPaths) throws IOException {
    setUp(vcsRootConfiguration, startDir);
    Collection<VcsRoot> vcsRoots = detect(startDir);
    assertRoots(asList(expectedPaths), getPaths(
      ContainerUtil.filter(vcsRoots, root -> {
        assert root.getVcs() != null;
        return root.getVcs().getKeyInstanceMethod().equals(myVcs.getKeyInstanceMethod());
      })
    ));
  }

  private void setUp(@NotNull VcsRootConfiguration vcsRootConfiguration, @Nullable VirtualFile startDir) throws IOException {
    initProject(vcsRootConfiguration);
    if (startDir != null) {
      startDir.refresh(false, true);
    }
  }
}
