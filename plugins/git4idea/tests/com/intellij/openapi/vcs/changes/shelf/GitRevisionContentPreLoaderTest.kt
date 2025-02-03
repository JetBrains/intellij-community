// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.impl.ContentRevisionCache
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitRevisionNumber
import git4idea.GitVcs
import git4idea.stash.GitRevisionContentPreLoader
import git4idea.test.GitSingleRepoTest
import git4idea.test.TestFile

class GitRevisionContentPreLoaderTest : GitSingleRepoTest() {

  override fun setUp() {
    super.setUp()
    git("config core.autocrlf false")
  }

  fun `test two files modification`() {
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
      val cache = ProjectLevelVcsManager.getInstance(project).contentRevisionCache
      val bytes = cache.getFromConstantCache(VcsUtil.getFilePath(file.file), revisionNumber, GitVcs.getKey(),
                                             ContentRevisionCache.UniqueType.REPOSITORY_CONTENT)
      assertNotNull("No content recorded for $file", bytes)
      assertEquals("Incorrect content for $file", content, String(bytes))
    }
  }
}