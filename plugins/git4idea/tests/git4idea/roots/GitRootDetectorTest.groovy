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
package git4idea.roots

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.test.GitMockVirtualFile
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.fail

/**
 * @author Kirill Likhodedov
 */
class GitRootDetectorTest {

  Project myProject

  @Before
  void setUp() {
  }

  @Test
  void "no roots in project"() {
    doTest gits: [],
           expected:  [],
           full: false
  }

  @Test
  void "project under single Git"() {
    doTest "."
  }

  @Test
  void "project with Git under it"() {
    doTest project:  [".idea", "bin", "src", "community"],
           gits:     ["community"],
           expected: ["community"],
           full:     false
  }

  @Test
  public void "project with all subdirs under Git should still be not fully controlled"() {
    doTest gits:     [".idea", "src", "community"],
           expected: [".idea", "src", "community"],
           full:     false
  }

  @Test
  void "project under Git above it"() {
    doTest ".."
  }

  @Test
  void "IDEA project"() {
    doTest ".", "./community", "./contrib"
  }

  @Test
  void "one above and one under"() {
    doTest "..", "./community"
  }

  @Test
  void "one above and one for project should show only project root"() {
    doTest gits:     ["..", "."],
           expected: ["."],
           full:     true
  }

  @Test
  void "one above and several under project"() {
    doTest "..", "./community", "./contrib"
  }

  @Test
  void "multiple above should be detected as one above"() {
    doTest gits:     ["..", "../.."],
           expected: [".."],
           full:     true
  }

  @Test
  void "unrelated root should not be detected"() {
    doTest gits:     ["../neighbour"],
           expected: [],
           full:     false
  }

  /**
   * Perform test. Map contains actual Git repositories to be created on disk,
   * and Git repositories expected to be detected by the GitRootDetector.
   * @param map
   */
  private void doTest(Map map) {
    initProject(map.gits, map.project)
    testInfo empty: map.expected.empty,
             full : map.full,
             roots: map.expected
  }

  /**
   * Shorthand, when all Git roots are expected to be detected, and project is fully under Git.
   * @param roots paths relative to the project dir. "..", ".", "./community" are accepted.
   */
  private void doTest(String... roots = []) {
    doTest gits:     roots.toList(),
           expected: roots.toList(),
           full:     true
  }

  /**
   * Creates the necessary temporary directories in the filesystem with empty ".git" directories for given roots.
   * And creates an instance of the project.
   * @param gitRoots path to actual .git roots, relative to the project dir.
   */
  private void initProject(Collection<String> gitRoots, Collection<String> projectStructure) {
    String projectDir = createDirs(gitRoots)
    myProject = [
      getBaseDir: { new GitMockVirtualFile(projectDir) }
    ] as Project
    createProjectStructure(projectStructure);
  }

  void createProjectStructure(Collection<String> paths) {
    paths.each { String path ->
      File file = new File(myProject.baseDir.path + "/" + path)
      file.mkdir()
    }
  }

  /**
   * @return path to the project
   */
  private static String createDirs(Collection<String> gitRoots) {
    if (gitRoots.empty) {
      return FileUtil.createTempDirectory("grdt", null);
    }

    File baseDir = createBaseTempDir()
    int maxDepth = findMaxDepthAboveProject(gitRoots)
    File projectDir = createChild(baseDir, maxDepth)
    gitRoots.each { String path ->
      File file = new File(projectDir.path + "/" + path)
      file.mkdirs()
      file.deleteOnExit()

      File gitDir = new File(file, ".git")
      gitDir.mkdir()
      gitDir.deleteOnExit()
    }
    return projectDir.path
  }

  private static File createBaseTempDir() {
    FileUtil.createTempDirectory("pref", null)
  }

  private static File createChild(File base, int depth) {
    File dir = base
    depth.times { dir = FileUtil.createTempDirectory(dir, "grdt", null)}
    dir
  }

  // Assuming that there are no ".." inside the path - only in the beginning
  static int findMaxDepthAboveProject(Collection<String> paths) {
    def len = { String path -> path.split("/").count("..") }
    len(paths.max(len))
  }

  void testInfo(Map expected) {
    assertInfo(expected, detect())
  }

  void assertInfo(Map expected, GitRootDetectInfo actual) {
    assertEquals(expected.empty, actual.empty())
    if (expected.full ^ actual.totallyUnderGit()) {
      fail("The project is unexpectedly ${actual.totallyUnderGit() ? "" : "not "}under Git\nRoots:${actual.roots.collect {"\n  * $it"}}\n" )
    }
    assertRoots(expected.roots, actual.roots)
  }

  void assertRoots(Collection<String> expectedRelativePaths, Collection<VirtualFile> actual) {
    assertEquals(toAbsolute(expectedRelativePaths).toSet(), getPaths(actual).toSet())
  }

  Collection<String> toAbsolute(Collection<String> relPaths) {
    relPaths.collect { new File(myProject.baseDir.path + "/" + it).getCanonicalPath() }
  }

  Collection<String> getPaths(Collection<VirtualFile> files) {
    files.collect { it.path }
  }

  private GitRootDetectInfo detect() {
    new GitRootDetector(myProject).detect()
  }

}