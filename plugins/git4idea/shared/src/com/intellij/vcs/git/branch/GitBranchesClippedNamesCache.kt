// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.vcs.git.ui.GitBranchPresentation
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
internal class GitBranchesClippedNamesCache(private val project: Project) : Disposable {
  private data class ClippedBranch(val clippedName: String, val length: Int)

  private val cache: Cache<String, ClippedBranch> =
    Caffeine.newBuilder()
      .expireAfterAccess(CACHE_ACCESS_EXPIRATION_MIN, TimeUnit.MINUTES)
      .maximumSize(CACHE_MAX_SIZE)
      .build()

  private val cacheDisposable = Disposer.newCheckedDisposable().also { Disposer.register(this, it) }

  /**
   * Truncate [branchName] or get from cache
   *
   * @param branchName branch name
   * @param maxBranchNameLength Amount of characters in branch name to preserve
   *
   * @see [truncateBranchName]
   */
  fun getOrCache(branchName: String, maxBranchNameLength: Int): String {
    if (cacheDisposable.isDisposed) return branchName

    val oldClippedBranch = cache.get(branchName) { truncateBranchName(branchName, maxBranchNameLength) }
    if (oldClippedBranch.length == maxBranchNameLength) return oldClippedBranch.clippedName

    val clippedBranch = truncateBranchName(branchName, maxBranchNameLength)
    cache.put(branchName, clippedBranch)
    return clippedBranch.clippedName
  }

  private fun truncateBranchName(branchName: String, maxBranchNameLength: Int): ClippedBranch {
    return ClippedBranch(GitBranchPresentation.truncateBranchName(project, branchName, maxBranchNameLength, 0, 0), maxBranchNameLength)
  }

  fun clear() = cache.invalidateAll()

  override fun dispose() {
    cache.invalidateAll()
  }

  companion object {
    private const val CACHE_MAX_SIZE = 1_000L
    private const val CACHE_ACCESS_EXPIRATION_MIN = 5L
  }
}