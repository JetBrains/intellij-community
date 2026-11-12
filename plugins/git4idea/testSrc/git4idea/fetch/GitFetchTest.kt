// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.fetch

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.assertSuccessfulNotification
import git4idea.fetch.GitFetchSupport.fetchSupport
import git4idea.i18n.GitBundle
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.cd
import git4idea.test.createBroRepo
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.log
import git4idea.test.prepareRemoteRepo
import git4idea.test.tac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class GitFetchTest {
  private val fixture = gitPlatformContextFixture(hasRemoteGitOperation = true)
  private val context: GitPlatformTestContext get() = fixture.get()

  private lateinit var repo: GitRepository
  private lateinit var broRepo: Path

  @BeforeEach
  fun setUp(): Unit = with(context) {
    repo = createRepository(project, projectNioRoot, true)
    cd(projectPath)

    val parent = prepareRemoteRepo(repo)
    git("push -u origin master")
    broRepo = createBroRepo("bro", parent)
    repo.update()
  }

  @Test
  fun `test fetch default remote`(): Unit = with(context) {
    cd(broRepo)
    val hash = tac("a.txt")
    git("push origin master")

    fetchSupport(project).fetchDefaultRemote(listOf(repo)).showNotification()

    cd(repo)
    assertThat(repo.log("--pretty=%H -1 origin/master"))
      .describedAs("The latest commit on origin/master is incorrect")
      .isEqualTo(hash)
    assertNotification()
  }

  @Test
  fun `test fetch specific remote`(): Unit = with(context) {
    val secondRemote = prepareSecondRemote()
    cd(broRepo)
    val hash1 = tac("a.txt")
    git("push second master")
    tac("b.txt")
    git("push origin master")

    fetchSupport(project).fetch(repo, secondRemote).showNotification()

    cd(repo)
    assertThat(repo.log("--pretty=%H -1 second/master"))
      .describedAs("The latest commit on second/master is incorrect")
      .isEqualTo(hash1)
    assertNotification()
  }

  @Test
  fun `test fetch all remotes`(): Unit = with(context) {
    prepareSecondRemote()
    cd(broRepo)
    val hash1 = tac("a.txt")
    git("push second master")
    val hash2 = tac("b.txt")
    git("push origin master")

    fetchSupport(project).fetchAllRemotes(listOf(repo)).showNotification()

    cd(repo)
    assertThat(repo.log("--pretty=%H -1 second/master"))
      .describedAs("The latest commit on second/master is incorrect")
      .isEqualTo(hash1)
    assertThat(repo.log("--pretty=%H -1 origin/master"))
      .describedAs("The latest commit on origin/master is incorrect")
      .isEqualTo(hash2)
    assertNotification()
  }

  private fun GitPlatformTestContext.assertNotification() {
    assertSuccessfulNotification("Fetch successful", GitBundle.message("auto.fetch.notification.suggestion.message"))
  }

  private fun GitPlatformTestContext.prepareSecondRemote(): GitRemote {
    val second = prepareRemoteRepo(repo, testNioRoot.resolve("second.git"), "second")
    cd(broRepo)
    git("remote add second '$second'")

    repo.update()
    return repo.remotes.first { it.name == "second" }
  }
}