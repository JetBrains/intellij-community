// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.fetch

import com.intellij.openapi.vcs.Executor.cd
import git4idea.fetch.GitFetchSupport.fetchSupport
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.test.*
import java.io.File

class GitFetchTest : GitPlatformTest() {

  private lateinit var repo: GitRepository
  private lateinit var broRepo : File

  override fun setUp() {
    super.setUp()

    repo = createRepository(project, projectPath, true)
    cd(projectPath)

    val parent = prepareRemoteRepo(repo)
    git("push -u origin master")
    broRepo = createBroRepo("bro", parent)
    repo.update()
  }

  fun `test fetch default remote`() {
    cd(broRepo)
    val hash = tac("a.txt")
    git("push origin master")

    fetchSupport(project).fetchDefaultRemote(listOf(repo)).showNotification()

    cd(repo)
    assertEquals("The latest commit on origin/master is incorrect", hash, log("--pretty=%H -1 origin/master"))
    assertSuccessfulNotification("<b>Fetch Successful</b>")
  }

  fun `test fetch specific remote`() {
    val secondRemote = prepareSecondRemote()
    cd(broRepo)
    val hash1 = tac("a.txt")
    git("push second master")
    tac("b.txt")
    git("push origin master")

    fetchSupport(project).fetch(repo, secondRemote).showNotification()

    cd(repo)
    assertEquals("The latest commit on second/master is incorrect", hash1, log("--pretty=%H -1 second/master"))
    assertSuccessfulNotification("<b>Fetch Successful</b>")
  }

  fun `test fetch all remotes`() {
    prepareSecondRemote()
    cd(broRepo)
    val hash1 = tac("a.txt")
    git("push second master")
    val hash2 = tac("b.txt")
    git("push origin master")

    fetchSupport(project).fetchAllRemotes(listOf(repo)).showNotification()

    cd(repo)
    assertEquals("The latest commit on second/master is incorrect", hash1, log("--pretty=%H -1 second/master"))
    assertEquals("The latest commit on origin/master is incorrect", hash2, log("--pretty=%H -1 origin/master"))
    assertSuccessfulNotification("<b>Fetch Successful</b>")
  }

  private fun prepareSecondRemote() : GitRemote {
    val second = prepareRemoteRepo(repo, File(testRoot, "second.git"), "second")
    cd(broRepo)
    git("remote add second '${second.path}'")

    repo.update()
    return repo.remotes.first { it.name == "second" }
  }
}