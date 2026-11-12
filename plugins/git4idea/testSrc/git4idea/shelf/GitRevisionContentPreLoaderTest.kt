// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.shelf

import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.impl.ContentRevisionCache
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitRevisionNumber
import git4idea.GitVcs
import git4idea.stash.GitRevisionContentPreLoader
import git4idea.test.GitSingleRepoContext
import git4idea.test.TestFile
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GitRevisionContentPreLoaderTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @BeforeEach
  fun setUp(): Unit = with(context) {
    git("config core.autocrlf false")
  }

  @Test
  fun `test two files modification`(): Unit = with(context) {
    val afile = file("a.txt")
    val initialA = "initialA\n"
    afile.create(initialA).addCommit("initial")
    afile.append("more changes in a\n")

    val bfile = file("b.txt")
    val initialB = "initialB\n"
    bfile.create(initialB).addCommit("initial")
    bfile.append("more changes in b\n")

    refresh()
    updateChangeListManager()

    val changes = changeListManager.allChanges
    val headRevision = GitRevisionNumber.resolve(project, repo.root, "HEAD")
    val preloader = GitRevisionContentPreLoader(project)

    preloader.preload(repo.root, changes)
    assertBaseContents(mapOf(afile to initialA, bfile to initialB), headRevision)

    val changesInOtherOrder = changes.reversed()
    preloader.preload(repo.root, changesInOtherOrder)
    assertBaseContents(mapOf(afile to initialA, bfile to initialB), headRevision)
  }

  private fun assertBaseContents(contents: Map<TestFile, String>, revisionNumber: GitRevisionNumber) {
    for ((file, content) in contents) {
      val cache = ProjectLevelVcsManager.getInstance(context.project).contentRevisionCache
      val bytes = cache.getFromConstantCache(VcsUtil.getFilePath(file.file.path, false), revisionNumber, GitVcs.getKey(),
                                             ContentRevisionCache.UniqueType.REPOSITORY_CONTENT)
      assertThat(bytes).describedAs("No content recorded for $file").isNotNull()
      assertThat(String(bytes!!)).describedAs("Incorrect content for $file").isEqualTo(content)
    }
  }
}
