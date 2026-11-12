// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.setupRepositories
import java.io.File
import java.nio.file.Path

// ---------------------------------------------------------------------------------------------------------------
// Main repository plus a nested "community" repository, each with a bro clone
// ---------------------------------------------------------------------------------------------------------------

internal interface GitMultiRepoUpdateContext : GitPlatformTestContext {
  val repository: GitRepository
  val community: GitRepository
  val bro: Path
  val bromunity: Path
}

internal fun TestFixture<GitPlatformTestContext>.gitMultiRepoUpdateFixture(): TestFixture<GitMultiRepoUpdateContext> = testFixture {
  val platformContext = init()
  with(platformContext) {
    val mainRepo = setupRepositories(projectPath, "parent", "bro")

    val communityDir = File(projectPath, "community")
    check(communityDir.mkdir()) { "Couldn't create $communityDir" }
    val enclosingRepo = setupRepositories(communityDir.path, "community_parent", "community_bro")

    mainRepo.projectRepo.update()
    enclosingRepo.projectRepo.update()

    val result = object : GitMultiRepoUpdateContext, GitPlatformTestContext by platformContext {
      override val repository = mainRepo.projectRepo
      override val community = enclosingRepo.projectRepo
      override val bro = mainRepo.bro
      override val bromunity = enclosingRepo.bro
    }
    initialized(result) {}
  }
}