// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.LinkDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.table.links.NavigateToCommit
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.test.GitSingleRepoContext
import git4idea.test.commit
import git4idea.test.file
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class GitLinkToCommitResolverTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  private lateinit var logData: VcsLogData
  private lateinit var visiblePack: VisiblePack

  @BeforeEach
  fun setUp() {
    with(context) {
      if (VcsProjectLog.ensureLogCreated(project)) {
        logData = VcsProjectLog.getInstance(project).dataManager!!
      }
    }
  }

  @Test
  fun `test resolve single fixup`(): Unit = with(context) {
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

    assertThat(links).hasSize(1)
    assertThat(links[0].target).isEqualTo(commitHash)
    assertThat(links[0].range).isEqualTo(TextRange.from(0, "fixup!".length))
    assertThat(links[0].range.substring(fixupCommitMsg)).isEqualTo("fixup!")
  }

  @Test
  fun `test resolve multiple prefixes`(): Unit = with(context) {
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
    assertThat(links).hasSize(1)
    assertThat(links[0].target).isEqualTo(commitHash)
    assertThat(links[0].range).isEqualTo(TextRange.from(0, "fixup!".length))
    assertThat(links[0].range.substring(fixup1CommitMsg)).isEqualTo("fixup!")

    links = resolver.getLinks(CommitId(fixup2CommitHash, repo.root))
    assertThat(links).hasSize(2)
    assertThat(links[0].target).isEqualTo(fixup1CommitHash.toString())
    assertThat(links[0].range).isEqualTo(TextRange.from(0, "fixup!".length))
    assertThat(links[0].range.substring(fixup2CommitMsg)).isEqualTo("fixup!")
    assertThat(links[1].target).isEqualTo(commitHash)
    assertThat(links[1].range).isEqualTo(TextRange.from(7, "fixup!".length))
    assertThat(links[1].range.substring(fixup2CommitMsg)).isEqualTo("fixup!")

    links = resolver.getLinks(CommitId(squashCommitHash, repo.root))
    assertThat(links).hasSize(1)
    assertThat(links[0].target).isEqualTo(commitHash)
    assertThat(links[0].range).isEqualTo(TextRange.from(7, "squash!".length))
    assertThat(links[0].range.substring(squashCommitMsg)).isEqualTo("squash!")
  }

  private fun GitLinkToCommitResolver.resolveLinks(commitId: CommitId, commitMessage: @NlsSafe String) {
    resolveLinks(logData, visiblePack.visibleGraph as VisibleGraphImpl, commitId, commitMessage,
                 Registry.intValue("vcs.log.render.commit.links.process.chunk"))
  }

  private fun GitSingleRepoContext.refreshVisibleGraph() {
    logData.refreshAndWait(repo, false)
    val dataPack = logData.graphData

    val visibleGraph = dataPack.permanentGraph.createVisibleGraph(PermanentGraph.Options.Default, null, null)
    visiblePack = VisiblePack(dataPack, visibleGraph, false, VcsLogFilterObject.collection())
  }

  private val LinkDescriptor.target get() = (this as NavigateToCommit).target
}
