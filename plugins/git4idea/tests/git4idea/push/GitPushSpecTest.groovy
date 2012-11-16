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
package git4idea.push

import git4idea.repo.GitRepository
import git4idea.test.GitExecutor
import git4idea.test.GitLightTest
import org.junit.After
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNull

/**
 * 
 * @author Kirill Likhodedov
 */
@Mixin(GitExecutor)
class GitPushSpecTest extends GitLightTest {

  GitRepository myRepository

  @Before
  @Override
  void setUp() {
    super.setUp()
    myRepository = createRepository(myProjectRoot)
    prepareRemoteRepo(myRepository)
  }

  @After
  @Override
  void tearDown() {
    super.tearDown()
  }

  @Test
  void "master tracks origin/master"() {
    git("push -u origin master")

    assertSpec("master", "origin/master")
  }

  @Test
  void "two tracked branches"() {
    git("push -u origin master")
    git("checkout -b feature")
    git("push -u origin feature")

    assertSpec("feature", "origin/feature")
  }

  @Test
  void "current branch tracks nothing, but other do"() {
    git("push -u origin master")
    git("checkout -b feature")

    assertSpec("feature", null)
  }

  @Test
  void "matching branch exist, but tracked doesn't"() {
    git("push origin master")

    assertSpec("master", null)
  }

  @Test
  void "matching different from tracked"() {
    git("push origin master")
    git("push -u origin master:tracked")

    assertSpec("master", "origin/tracked")
  }

  void "not testing detached HEAD"() {
    // push action should not be available at this point
  }

  void "not testing fresh repository"() {
    // push action should not be available at this point
  }

  void assertSpec(String local, String remote) {
    GitPushSpec spec = GitPushSpec.collect(myRepository)
    assertEquals("Incorrect local branch to push", local, spec.getSource().getName())
    if (remote != null) {
      assertEquals("Incorrect remote branch to push", remote, spec.getDest().getNameForLocalOperations())
    }
    else {
      assertNull("There should be no destination branch to push", remote)
    }
  }

}

