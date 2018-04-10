// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import git4idea.config.UpdateMethod
import git4idea.test.GitSingleRepoTest

class GitUpdateMethodTest: GitSingleRepoTest() {

  fun `test merge is default if no config`() {
    assertDefaultUpdateMethod(UpdateMethod.MERGE)
  }

  fun `test rebase is default if branch-master-rebase is set to interactive`() {
    git("config branch.master.rebase interactive")
    assertDefaultUpdateMethod(UpdateMethod.REBASE)
  }

  fun `test rebase is default if branch-master-rebase is set to preserve`() {
    git("config branch.master.rebase preserve")
    assertDefaultUpdateMethod(UpdateMethod.REBASE)
  }

  fun `test rebase is default if pull-rebase is set to true`() {
    git("config pull.rebase true")
    assertDefaultUpdateMethod(UpdateMethod.REBASE)
  }

  fun `test rebase is default if pull-rebase is set to interactive`() {
    git("config pull.rebase interactive")
    assertDefaultUpdateMethod(UpdateMethod.REBASE)
  }

  fun `test rebase is default if pull-rebase is set to preserve`() {
    git("config pull.rebase preserve")
    assertDefaultUpdateMethod(UpdateMethod.REBASE)
  }

  fun `test branch config overrides pull-rebase`() {
    git("config pull.rebase true")
    git("config branch.master.rebase false")
    assertDefaultUpdateMethod(UpdateMethod.MERGE)
  }

  private fun assertDefaultUpdateMethod(expectedMethod: UpdateMethod) {
    assertEquals("Default update method is incorrect", expectedMethod, GitUpdater.resolveUpdateMethod(repo))
  }
}