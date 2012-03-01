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
import git4idea.test.GitMockVcsManager
import git4idea.test.GitTestPlatformFacade
import org.junit.Before
import org.junit.Test

import static git4idea.test.GitGTestUtil.toAbsolute
import static junit.framework.Assert.assertEquals
import git4idea.test.GitMockVirtualFile

/**
 * 
 * @author Kirill Likhodedov
 */
class GitRootErrorsFinderTest extends AbstractGitRootTest {

  Project myProject
  GitTestPlatformFacade myPlatformFacade
  GitMockVcsManager myVcsManager

  @Before
  void setUp() {
    myPlatformFacade = new GitTestPlatformFacade()
  }

  @Test
  void "No roots => no errors"() {
    doTest git:    [],
           roots:  [],
           errors: []
  }
  
  @Test
  void "Same 1 root in both => no errors"() {
    doTest git:    ["."],
           roots:  ["."],
           errors: []
  }
  
  @Test
  void "Same 2 roots in both => no errors"() {
    doTest git:    ["..", "community"],
           roots:  ["..", "community"],
           errors: []
  }
  
  @Test
  void "One git, no VCS roots => error"() {
    doTest git:    ["."],
           roots:  [],
           errors: [unreg : ["."]]
  }
  
  @Test
  void "One VCS root, no gits => error"() {
    doTest git:    [],
           roots:  ["."],
           errors: [extra: ["."]]
  }

  @Test
  void "One root, but different => 2 errors"() {
    doTest git:    ["."],
           roots:  ["community"],
           errors: [unreg: ["."], extra: ["community"]]
  }

  @Test
  void "Two roots, one matching, one different => 2 errors"() {
    doTest git:    [".", "community"],
           roots:  [".", "contrib"],
           errors: [unreg: ["community"], extra: ["contrib"]]
  }

  @Test
  void "Two roots in git, one matching in VCS => error"() {
    doTest git:    [".", "community"],
           roots:  ["."],
           errors: [unreg: ["community"]]
  }

  @Test
  void "Two roots, both not matching => 4 errors"() {
    doTest git:    ["..", "community"],
           roots:  [".", "contrib"],
           errors: [unreg: ["..", "community"], extra: [".", "contrib"]]
  }

  private void doTest(Map map) {
    myProject = initProject(map.git, [])
    myVcsManager = (GitMockVcsManager) myPlatformFacade.getVcsManager(myProject)

    addVcsRoots(map.roots)

    Collection<GitRootError> expected = new ArrayList<GitRootError>();
    expected.addAll map.errors.unreg.collect { unreg(it) }
    expected.addAll map.errors.extra.collect { extra(it) }

    Collection<GitRootError> actual = new GitRootErrorsFinder(myProject, myPlatformFacade).find()
    assertEquals expected.toSet(), actual.toSet()
  }

  void addVcsRoots(Collection<String> relativeRoots) {
    relativeRoots.each {
      String root = toAbsolute(it, myProject)
      myVcsManager.addRoots(root)
    }
  }

  GitRootError unreg(String path) {
    return new GitRootError(GitRootError.Type.UNREGISTERED_ROOT, new GitMockVirtualFile(toAbsolute(path, myProject)))
  }

  GitRootError extra(String path) {
    return new GitRootError(GitRootError.Type.EXTRA_ROOT, new GitMockVirtualFile(toAbsolute(path, myProject)))
  }

  
}
