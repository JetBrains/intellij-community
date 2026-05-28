// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.util.containers.nullize
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchUtil
import git4idea.config.GitConfigUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

/**
 * Reads information from the git config command response and parses it to actual objects.
 *
 * Currently, doesn't read all the information: just general information about remotes and branch tracking.
 */
@ApiStatus.Internal
class GitConfig private constructor(
  private val remotes: List<Remote>,
  private val urlSubstitutions: List<UrlSubstitution>,
  private val trackedInfos: List<BranchConfig>,
  private val core: Core,
) {
  /**
   * Returns Git remotes defined in `.git/config`.
   *
   * Remote is returned with all transformations (such as `pushUrl, url.<base>.insteadOf`) already applied to it.
   * See [GitRemote] for details.
   *
   * **Note:** remotes can be defined separately in `.git/remotes` directory (obsolete mechanism), by creating a file for each remote with
   * remote parameters written in the file. This method returns ONLY remotes defined in `.git/config`.
   * @return Git remotes defined in `.git/config`.
   */
  @VisibleForTesting
  fun parseRemotes(): Set<GitRemote> {
    if (urlSubstitutions.isEmpty()) {
      return mapConfiguredRemotes { remote ->
        createGitRemote(remote, remote.urls, remote.pushUrls.nullize() ?: remote.urls)
      }
    }
    val (substitutions, pushOnlySubstitutions) = urlSubstitutions.partition { !it.pushOnly }
      .let { (fetch, pushOnly) -> fetch.convertToSortedList() to pushOnly.convertToSortedList() }

    return mapConfiguredRemotes { remote ->
      val urls = remote.urls.substitutePrefixes(substitutions)
      val pushUrls = if (remote.pushUrls.isNotEmpty()) {
        // for explicitly set pushUrls only insteadOf substitutions will be used
        remote.pushUrls.substitutePrefixes(substitutions)
      }
      else if (pushOnlySubstitutions.isNotEmpty()) {
        remote.urls.substitutePrefixes(pushOnlySubstitutions)
      }
      else urls
      createGitRemote(remote, urls, pushUrls)
    }
  }

  private fun createGitRemote(remote: Remote, urls: List<String>, pushUrls: List<String>): GitRemote =
    GitRemote(remote.name, urls, pushUrls, remote.fetchSpecs, remote.pushSpec)

  private fun mapConfiguredRemotes(transform: (Remote) -> GitRemote) =
    remotes.asSequence().filter { it.urls.isNotEmpty() }.map(transform).toSet()

  private fun List<UrlSubstitution>.convertToSortedList(): List<UrlSubstitution> =
    asSequence().distinctBy { it.prefix }.sortedByDescending { it.prefix.length }.toList()

  private fun List<String>.substitutePrefixes(substitutions: List<UrlSubstitution>) = map { url ->
    substitutions.find { url.startsWith(it.prefix) }?.substitutePrefix(url) ?: url
  }

  private fun UrlSubstitution.substitutePrefix(url: String) = substitution + url.substring(prefix.length)

  /**
   * Create branch tracking information based on the information defined in `.git/config`.
   */
  @VisibleForTesting
  fun parseTrackInfos(
    localBranches: Collection<GitLocalBranch>,
    remoteBranches: Collection<GitRemoteBranch>,
  ): Set<GitBranchTrackInfo> =
    trackedInfos.mapNotNull { config ->
      convertBranchConfig(config, localBranches, remoteBranches)
    }.toSet()

  /**
   * Return core info
   */
  fun parseCore(): Core {
    return core
  }

  private data class Remote(
    val name: String,
    val fetchSpecs: List<String>,
    val pushSpec: List<String>,
    val urls: List<String>,
    val pushUrls: List<String>,
  )

  private data class UrlSubstitution(
    val prefix: String,
    // null means no entry, i.e. nothing to substitute. Empty string means substituting everything
    val substitution: String,
    val pushOnly: Boolean,
  )

  private data class BranchConfig(
    val name: String,
    val remote: String?,
    val merge: String?,
    val rebase: String?,
  )

  class Core(
    val hooksPath: String?,
  )

  companion object {
    private val LOG = Logger.getInstance(GitConfig::class.java)

    private const val SVN_REMOTE_SECTION = "svn-remote"
    private const val GIT_REMOTE_SECTION = "remote"
    private const val URL_SECTION = "url"
    private const val BRANCH_INFO_SECTION = "branch"
    private const val CORE_SECTION = "core"

    /**
     * Creates an instance of GitConfig by reading information from the specified `.git/config` file.
     *
     * If some section is invalid, it is skipped.
     */
    @JvmStatic
    @VisibleForTesting
    fun read(project: Project?, root: Path): GitConfig {
      val configurations: Map<String, List<String>> = try {
        GitConfigUtil.getValues(project, root, null)
      }
      catch (_: VcsException) {
        return GitConfig(listOf(), listOf(), listOf(), Core(null))
      }

      val remotes = mutableListOf<Remote>()
      val urls = mutableListOf<UrlSubstitution>()
      val trackedInfos = mutableListOf<BranchConfig>()
      val core = createCore(configurations)

      val sectionVariables = configurations.keys.mapNotNull { parseSectionNameAndVariable(it) }.toSet()

      sectionVariables.forEach { (sectionName, sectionVariable) ->
        when (sectionName) {
          SVN_REMOTE_SECTION, GIT_REMOTE_SECTION -> remotes.add(createRemote(configurations, sectionName, sectionVariable))
          URL_SECTION -> urls.addAll(createUrl(configurations, sectionName, sectionVariable))
          BRANCH_INFO_SECTION -> trackedInfos.add(createBranchConfig(configurations, sectionName, sectionVariable))
        }
      }

      return GitConfig(remotes, urls, trackedInfos, core)
    }

    private fun parseSectionNameAndVariable(key: String): Pair<String, String>? {
      val firstDotIndex = key.indexOf('.')
      if (firstDotIndex < 0) {
        return null
      }
      val lastDotIndex = key.lastIndexOf('.')
      if (firstDotIndex == lastDotIndex) {
        return null
      }
      return key.substring(0, firstDotIndex) to key.substring(firstDotIndex + 1, lastDotIndex)
    }

    private fun convertBranchConfig(
      branchConfig: BranchConfig,
      localBranches: Collection<GitLocalBranch>,
      remoteBranches: Collection<GitRemoteBranch>,
    ): GitBranchTrackInfo? {
      val branchName = branchConfig.name
      val remoteName = branchConfig.remote
      val mergeName = branchConfig.merge
      val rebaseName = branchConfig.rebase

      if (mergeName.isNullOrBlank() && rebaseName.isNullOrBlank()) {
        LOG.debug("No branch.$branchName.merge/rebase item in the .git/config")
        return null
      }
      if (remoteName.isNullOrBlank()) {
        LOG.debug("No branch.$branchName.remote item in the .git/config")
        return null
      }

      val merge = mergeName != null
      val remoteBranchName = StringUtil.unquoteString((if (merge) mergeName else rebaseName)!!)

      val localBranch: GitLocalBranch? = findLocalBranch(branchName, localBranches)
      val remoteBranch: GitRemoteBranch? = findRemoteBranch(remoteBranchName, remoteName, remoteBranches)
      if (localBranch == null || remoteBranch == null) {
        // obsolete record in .git/config: local or remote branch doesn't exist, but the tracking information wasn't removed
        LOG.debug("localBranch: $localBranch, remoteBranch: $remoteBranch")
        return null
      }
      return GitBranchTrackInfo(localBranch, remoteBranch, merge)
    }

    private fun findLocalBranch(branchName: String, localBranches: Collection<GitLocalBranch>): GitLocalBranch? {
      val name = GitBranchUtil.stripRefsPrefix(branchName)
      return localBranches.find { input -> input.name == name }
    }

    fun findRemoteBranch(
      remoteBranchName: String,
      remoteName: String,
      remoteBranches: Collection<GitRemoteBranch>,
    ): GitRemoteBranch? {
      val branchName = GitBranchUtil.stripRefsPrefix(remoteBranchName)
      return remoteBranches.find { branch ->
        branch.nameForRemoteOperations == branchName &&
        branch.remote.name == remoteName
      }
    }

    private fun createBranchConfig(
      configurations: Map<String, List<String>>,
      sectionName: String,
      branchName: String,
    ): BranchConfig {
      val sectionKey = "$sectionName.$branchName"

      val remote = configurations["$sectionKey.remote"]?.lastOrNull()
      val merge = configurations["$sectionKey.merge"]?.lastOrNull()
      val rebase = configurations["$sectionKey.rebase"]?.lastOrNull()

      return BranchConfig(branchName, remote, merge, rebase)
    }

    private fun createRemote(
      configurations: Map<String, List<String>>,
      sectionName: String,
      remoteName: String,
    ): Remote {
      val sectionKey = "$sectionName.$remoteName"

      val fetchSpecs = configurations["$sectionKey.fetch"] ?: emptyList()
      val pushSpecs = configurations["$sectionKey.push"] ?: emptyList()
      val urls = configurations["$sectionKey.url"] ?: emptyList()
      val pushUrls = configurations["$sectionKey.pushurl"] ?: emptyList()

      return Remote(remoteName, fetchSpecs, pushSpecs, urls, pushUrls)
    }

    private fun createUrl(
      configurations: Map<String, List<String>>,
      sectionName: String,
      url: String,
    ): List<UrlSubstitution> {
      val sectionKey = "$sectionName.$url"

      return buildList {
        configurations["$sectionKey.insteadof"]?.forEach {
          add(UrlSubstitution(it, url, false))
        }
        configurations["$sectionKey.pushinsteadof"]?.forEach {
          add(UrlSubstitution(it, url, true))
        }
      }
    }

    private fun createCore(configurations: Map<String, List<String>>) =
      Core(configurations["$CORE_SECTION.hookspath"]?.lastOrNull())
  }
}
