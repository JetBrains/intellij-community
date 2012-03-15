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
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.VcsRootError
import git4idea.test.GitMockVcsManager
import git4idea.test.GitTestPlatformFacade
import org.junit.Before
import org.junit.Test

import static git4idea.test.GitGTestUtil.toAbsolute
import static junit.framework.Assert.assertEquals

/**
 * 
 * @author Kirill Likhodedov
 */
class GitRootErrorsFinderTest extends AbstractGitRootTest {

  static final String PROJECT = VcsDirectoryMapping.PROJECT_CONSTANT

  Project myProject
  GitTestPlatformFacade myPlatformFacade
  GitMockVcsManager myVcsManager

  @Before
  void setUp() {
    myPlatformFacade = new GitTestPlatformFacade()
  }

  @Test
  void "No roots, then no errors"() {
    doTest git:    [],
           roots:  [],
           errors: []
  }
  
  @Test
  void "Same 1 root in both, then no errors"() {
    doTest git:    ["."],
           roots:  ["."],
           errors: []
  }
  
  @Test
  void "Same 2 roots in both, then no errors"() {
    doTest git:    ["..", "community"],
           roots:  ["..", "community"],
           errors: []
  }
  
  @Test
  void "One git, no VCS roots, then error"() {
    doTest git:    ["."],
           roots:  [],
           errors: [unreg : ["."]]
  }
  
  @Test
  void "One VCS root, no gits, then error"() {
    doTest git:    [],
           roots:  ["."],
           errors: [extra: ["."]]
  }

  @Test
  void "One root, but different, then 2 errors"() {
    doTest git:    ["."],
           roots:  ["community"],
           errors: [unreg: ["."], extra: ["community"]]
  }

  @Test
  void "Two roots, one matching, one different, then 2 errors"() {
    doTest git:    [".", "community"],
           roots:  [".", "contrib"],
           errors: [unreg: ["community"], extra: ["contrib"]]
  }

  @Test
  void "Two roots in git, one matching in VCS, then error"() {
    doTest git:    [".", "community"],
           roots:  ["."],
           errors: [unreg: ["community"]]
  }

  @Test
  void "Two roots, both not matching, then 4 errors"() {
    doTest git:    ["..", "community"],
           roots:  [".", "contrib"],
           errors: [unreg: ["..", "community"], extra: [".", "contrib"]]
  }
  
  @Test
  void "Project root, no gits, then error about extra root"() {
    doTest content_roots: ["."],
           git:    [],
           roots:  [PROJECT],
           errors: [extra: [PROJECT]]
  }
  
  @Test
  void "Project root, full under git, then correct"() {
    doTest content_roots: ["."],
           git:    ["."],
           roots:  [PROJECT],
           errors: []
  }
  
  @Test
  void "Project root, git for a content root below project, then correct"() {
    doTest content_roots: [".", "content_root"],
           git:           [""],
           roots:         [PROJECT],
           errors:        []
  }
  
  @Test
  void "Project root, git below project folder not in a content root, then correct since folders are auto-detected"() {
    doTest content_roots: ["."],
           git:    ["community"],
           roots:  [PROJECT],
           errors: []
  }

  @Test
  void "Project root, git for full project, content root, linked source, folder below project, then correct since folders are detected"() {
    doTest content_roots: [".", "content_root", "../linked_source_root"],
           git:           [".", "content_root", "../linked_source_root", "folder"],
           roots:         [PROJECT],
           errors:        []
  }

  @Test
  void "Project root, root for folder, git for full project, content root, linked source, folder below project, then correct"() {
    doTest content_roots: [".", "content_root", "../linked_source_root"],
           git:           [".", "content_root", "../linked_source_root", "folder"],
           roots:         [PROJECT, "folder"],
           errors:        []
  }

  @Test
  void "Project root, git like in IDEA project, then correct"() {
    doTest content_roots: [".", "community", "contrib"],
           git:           [".", "community", "contrib"],
           roots:         [PROJECT],
           errors:        []
  }

  private void doTest(Map map) {
    myProject = initProject(map.git, [], map.content_roots)
    myVcsManager = (GitMockVcsManager) myPlatformFacade.getVcsManager(myProject)

    addVcsRoots(map.roots)

    Collection<VcsRootError> expected = new ArrayList<VcsRootError>();
    expected.addAll map.errors.unreg.collect { unreg(it) }
    expected.addAll map.errors.extra.collect { extra(it) }

    Collection<VcsRootError> actual = new GitRootErrorsFinder(myProject, myPlatformFacade).find()
    assertEquals expected.toSet(), actual.toSet()
  }

  void addVcsRoots(Collection<String> relativeRoots) {
    relativeRoots.each {
      if (it.equals(PROJECT)) {
        myVcsManager.setProjectRootMapping()
      }
      else {
        String root = toAbsolute(it, myProject)
        myVcsManager.addRoots(root)
      }
    }
  }

  VcsRootError unreg(String path) {
    return new VcsRootError(VcsRootError.Type.UNREGISTERED_ROOT, toAbsolute(path, myProject))
  }

  VcsRootError extra(String path) {
    return new VcsRootError(VcsRootError.Type.EXTRA_MAPPING, path.equals(PROJECT) ? PROJECT : toAbsolute(path, myProject))
  }

  
}
