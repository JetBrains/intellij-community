// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import git4idea.repo.GitRepository
import java.io.File
import java.nio.file.Path

abstract class GitMultiRepoUpdateBaseTest : GitUpdateBaseTest() {
  protected lateinit var repository: GitRepository
  protected lateinit var community: GitRepository
  protected lateinit var bro: Path
  protected lateinit var bromunity: Path

  override fun setUp() {
    super.setUp()

    val mainRepo = setupRepositories(projectPath, "parent", "bro")
    repository = mainRepo.projectRepo
    bro = mainRepo.bro

    val communityDir = File(projectPath, "community")
    assertTrue(communityDir.mkdir())
    val enclosingRepo = setupRepositories(communityDir.path, "community_parent", "community_bro")
    community = enclosingRepo.projectRepo
    bromunity = enclosingRepo.bro

    repository.update()
    community.update()
  }
}