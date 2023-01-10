// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import git4idea.ui.branch.GitBranchPopupActions.truncateBranchName
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class GitBranchesClippedNamesCache(private val project: Project) : Disposable {
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
   * @param branchName Full branch name
   * @param branchNameToClip Branch name or part of branch name to clip
   * @param maxBranchNameLength Amount of characters in branch name to preserve
   *
   * @see [truncateBranchName]
   */
  fun getOrCache(branchName: String, branchNameToClip: String, maxBranchNameLength: Int): String {
    if (cacheDisposable.isDisposed) return ""

    val (existingClippedName, existingMaxBranchNameLength) =
      cache.get(branchName) {
        ClippedBranch(truncateBranchName(project, branchNameToClip, maxBranchNameLength, 0, 0), maxBranchNameLength)
      }

    if (existingMaxBranchNameLength == maxBranchNameLength) return existingClippedName

    val clippedBranch = ClippedBranch(truncateBranchName(project, branchNameToClip, maxBranchNameLength, 0, 0), maxBranchNameLength)
    cache.put(branchName, clippedBranch)
    return clippedBranch.clippedName
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
