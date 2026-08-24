// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.testFramework.junit5.TestApplication
import git4idea.config.UpdateMethod
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitUpdateMethodTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test merge is default if no config`() {
    assertDefaultUpdateMethod(UpdateMethod.MERGE)
  }

  @Test
  fun `test rebase is default if branch-master-rebase is set to interactive`(): Unit = with(context) {
    git("config branch.master.rebase interactive")
    assertDefaultUpdateMethod(UpdateMethod.REBASE)
  }

  @Test
  fun `test rebase is default if branch-master-rebase is set to preserve`(): Unit = with(context) {
    git("config branch.master.rebase preserve")
    assertDefaultUpdateMethod(UpdateMethod.REBASE)
  }

  @Test
  fun `test rebase is default if pull-rebase is set to true`(): Unit = with(context) {
    git("config pull.rebase true")
    assertDefaultUpdateMethod(UpdateMethod.REBASE)
  }

  @Test
  fun `test rebase is default if pull-rebase is set to interactive`(): Unit = with(context) {
    git("config pull.rebase interactive")
    assertDefaultUpdateMethod(UpdateMethod.REBASE)
  }

  @Test
  fun `test rebase is default if pull-rebase is set to preserve`(): Unit = with(context) {
    git("config pull.rebase preserve")
    assertDefaultUpdateMethod(UpdateMethod.REBASE)
  }

  @Test
  fun `test branch config overrides pull-rebase`(): Unit = with(context) {
    git("config pull.rebase true")
    git("config branch.master.rebase false")
    assertDefaultUpdateMethod(UpdateMethod.MERGE)
  }

  private fun assertDefaultUpdateMethod(expectedMethod: UpdateMethod) {
    assertThat(GitUpdater.resolveUpdateMethod(context.repo))
      .describedAs("Default update method is incorrect")
      .isEqualTo(expectedMethod)
  }
}
