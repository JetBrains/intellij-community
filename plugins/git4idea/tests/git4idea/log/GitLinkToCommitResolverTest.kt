// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.LinkDescriptor
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogRefresherTest.LogRefresherTestHelper
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.table.links.NavigateToCommit
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.test.GitSingleRepoTest
import git4idea.test.commit

class GitLinkToCommitResolverTest : GitSingleRepoTest() {

  private lateinit var logData: VcsLogData

  private lateinit var logRefresherHelper: LogRefresherTestHelper
  private lateinit var visiblePack: VisiblePack

  override fun setUp() {
    super.setUp()

    if (VcsProjectLog.ensureLogCreated(project)) {
      val projectLog = VcsProjectLog.getInstance(project)
      logData = projectLog.dataManager!!
      logRefresherHelper = LogRefresherTestHelper(logData, VcsLogData.getRecentCommitsCount())
    }
  }

  override fun tearDown() {
    try {
      logRefresherHelper.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test resolve single fixup`() {
    val fixupCommitMsg = "fixup! [subsystem] add file 1"
    val commitMsg = "[subsystem] add file 1"

    file("1.txt").create("File 1 content").add()
    val commitHash = repo.commit(commitMsg)
    file("2.txt").create("File 2 content").add()
    val fixupCommitHash = HashImpl.build(repo.commit(fixupCommitMsg))

    refreshVisibleGraph()

    val resolver = project.service<GitLinkToCommitResolver>()
    resolver.resolveLinks(CommitId(fixupCommitHash, repo.root), fixupCommitMsg)
    val links = resolver.getLinks(CommitId(fixupCommitHash, repo.root))

    assertTrue(links.size == 1)
    assertEquals(links[0].target, commitHash)
    assertEquals(links[0].range, TextRange.from(0, "fixup!".length))
    assertEquals(links[0].range.substring(fixupCommitMsg), "fixup!")
  }

  fun `test resolve multiple prefixes`() {
    val squashCommitMsg = "fixup! squash! add file 1"
    val fixup2CommitMsg = "fixup! fixup! add file 1"
    val fixup1CommitMsg = "fixup! add file 1"
    val commitMsg = "add file 1"

    file("1.txt").create("File 1 content").add()
    val commitHash = repo.commit(commitMsg)
    file("2.txt").create("File 2 content").add()
    val fixup1CommitHash = HashImpl.build(repo.commit(fixup1CommitMsg))
    file("3.txt").create("File 3 content").add()
    val fixup2CommitHash = HashImpl.build(repo.commit(fixup2CommitMsg))
    file("4.txt").create("File 4 content").add()
    val squashCommitHash = HashImpl.build(repo.commit(squashCommitMsg))

    refreshVisibleGraph()

    val resolver = project.service<GitLinkToCommitResolver>()
    resolver.resolveLinks(CommitId(fixup1CommitHash, repo.root), fixup1CommitMsg)
    resolver.resolveLinks(CommitId(fixup2CommitHash, repo.root), fixup2CommitMsg)
    resolver.resolveLinks(CommitId(squashCommitHash, repo.root), squashCommitMsg)

    var links = resolver.getLinks(CommitId(fixup1CommitHash, repo.root))
    assertTrue(links.size == 1)
    assertEquals(links[0].target, commitHash)
    assertEquals(links[0].range, TextRange.from(0, "fixup!".length))
    assertEquals(links[0].range.substring(fixup1CommitMsg), "fixup!")

    links = resolver.getLinks(CommitId(fixup2CommitHash, repo.root))
    assertTrue(links.size == 2)
    assertEquals(links[0].target, fixup1CommitHash.toString())
    assertEquals(links[0].range, TextRange.from(0, "fixup!".length))
    assertEquals(links[0].range.substring(fixup2CommitMsg), "fixup!")
    assertEquals(links[1].target, commitHash)
    assertEquals(links[1].range, TextRange.from(7, "fixup!".length))
    assertEquals(links[1].range.substring(fixup2CommitMsg), "fixup!")

    links = resolver.getLinks(CommitId(squashCommitHash, repo.root))
    assertTrue(links.size == 1)
    assertEquals(links[0].target, commitHash)
    assertEquals(links[0].range, TextRange.from(7, "squash!".length))
    assertEquals(links[0].range.substring(squashCommitMsg), "squash!")
  }

  private fun GitLinkToCommitResolver.resolveLinks(commitId: CommitId, commitMessage: @NlsSafe String) {
    resolveLinks(logData, visiblePack, commitId, commitMessage, Registry.intValue("vcs.log.render.commit.links.process.chunk"))
  }

  private fun refreshVisibleGraph() {
    logRefresherHelper.initAndWaitForFirstRefresh()
    val dataPack = logRefresherHelper.dataPack

    val visibleGraph = dataPack.permanentGraph.createVisibleGraph(PermanentGraph.Options.Default, null, null)
    visiblePack = VisiblePack(dataPack, visibleGraph, false, VcsLogFilterObject.collection())
  }

  private val LinkDescriptor.target get() = (this as NavigateToCommit).target
}
