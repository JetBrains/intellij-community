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
import com.intellij.openapi.vfs.VirtualFile
import git4idea.test.GitGTestUtil
import org.junit.After
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.fail

/**
 * @author Kirill Likhodedov
 */
class GitRootDetectorTest extends AbstractGitRootTest {

  Project myProject

  @Before
  void setUp() {
    super.setUp();
  }

  @After
  void tearDown() {
    super.tearDown();
  }

  @Test
  void "no roots in project"() {
    doTest gits: [],
           expected:  [],
           full: false,
           below: false
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
           full:     false,
           below:    false
  }

  @Test
  public void "project with all subdirs under Git should still be not fully controlled"() {
    doTest gits:     [".idea", "src", "community"],
           expected: [".idea", "src", "community"],
           full:     false,
           below:    false
  }

  @Test
  void "project under Git above it"() {
    doTest gits:      [".."],
           expected:  [".."],
           full:      true,
           below:     true
  }

  @Test
  void "IDEA project"() {
    doTest ".", "./community", "./contrib"
  }

  @Test
  void "one above and one under"() {
    doTest gits:     ["..", "./community"],
           expected: ["..", "./community"],
           full:     true,
           below:    true
  }

  @Test
  void "one above and one for project should show only project root"() {
    doTest gits:     ["..", "."],
           expected: ["."],
           full:     true,
           below:    false
  }

  @Test
  void "one above and several under project"() {
    doTest gits:     ["..", "./community", "./contrib"],
           expected: ["..", "./community", "./contrib"],
           full:     true,
           below:    true
  }

  @Test
  void "multiple above should be detected as one above"() {
    doTest gits:     ["..", "../.."],
           expected: [".."],
           full:     true,
           below:    true
  }

  @Test
  void "unrelated root should not be detected"() {
    doTest gits:     ["../neighbour"],
           expected: [],
           full:     false,
           below:    false
  }

  @Test
  void "linked source root alone should be detected"() {
    doTest content_roots: ["../linked_root"],
           gits:          ["../linked_root"],
           expected:      ["../linked_root"],
           full:          false,
           below:         false
  }

  @Test
  void "linked source root and project root should be detected"() {
    doTest content_roots: ["../linked_root"],
           gits:          [".", "../linked_root"],
           expected:      [".", "../linked_root"],
           full:          true,
           below:         false
  }

  @Test
  void "linked source below Git"() {
    doTest content_roots: ["../linked_root/src"],
           gits:          [".", "../linked_root"],
           expected:      [".", "../linked_root"],
           full:          true,
           below:         false
  }

  @Test
  // This is a test of performance optimization via limitation: don't scan deep though the whole VFS, i.e. don't detect deep roots
  void "don't scan deeper than 2 levels below a content root"() {
    doTest content_roots:  ["content_root"],
           gits:           ["community", "content_root/lev1/lev2", "content_root2/lev1/lev2/lev3"],
           expected:       ["community", "content_root/lev1/lev2"],
           full:           false,
           below:          false
  }

  /**
   * Perform test. Map contains actual Git repositories to be created on disk,
   * and Git repositories expected to be detected by the GitRootDetector.
   * @param map
   */
  private void doTest(Map map) {
    myProject = initProject(map.gits, map.project, map.content_roots)
    testInfo empty: map.expected.empty,
             full : map.full,
             roots: map.expected,
             below: map.below
  }

  /**
   * Shorthand, when all Git roots are expected to be detected, and project is fully under Git.
   * @param roots paths relative to the project dir. "..", ".", "./community" are accepted.
   */
  private void doTest(String... roots = []) {
    doTest gits:     roots.toList(),
           expected: roots.toList(),
           full:     true,
           below:    false
  }

  void testInfo(Map expected) {
    assertInfo(expected, detect())
  }

  void assertInfo(Map expected, GitRootDetectInfo actual) {
    assertEquals(expected.empty, actual.empty())
    if (expected.full ^ actual.totallyUnderGit()) {
      fail("The project is unexpectedly ${actual.totallyUnderGit() ? "" : "not "}under Git${roots(actual.roots)}")
    }
    if (expected.below ^ actual.projectIsBelowGit()) {
      fail("The project is unexpectedly ${actual.projectIsBelowGit() ? "below" : "not below"} Git${roots(actual.roots)}")
    }
    assertRoots(expected.roots, actual.roots)
  }

  static String roots(Collection roots) {
    "\nRoots:${roots.collect {"\n  * $it"}}\n"
  }

  void assertRoots(Collection<String> expectedRelativePaths, Collection<VirtualFile> actual) {
    assertEquals(GitGTestUtil.toAbsolute(expectedRelativePaths, myProject).toSet(), getPaths(actual).toSet())
  }

  static Collection<String> getPaths(Collection<VirtualFile> files) {
    files.collect { it.path }
  }

  private GitRootDetectInfo detect() {
    new GitRootDetector(myProject, myPlatformFacade).detect()
  }

}