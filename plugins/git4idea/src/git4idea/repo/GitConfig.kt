// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchUtil
import git4idea.config.GitConfigUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

/**
 * Reads information from the `.git/config` file, and parses it to actual objects.
 *
 * Currently doesn't read all the information: just general information about remotes and branch tracking.
 *
 * TODO: note, that other git configuration files (such as ~/.gitconfig) are not handled yet.
 */
@ApiStatus.Internal
class GitConfig private constructor(
  private val remotes: List<Remote>,
  private val urls: List<Url>,
  private val trackedInfos: List<BranchConfig>,
  private val core: Core,
) {
  /**
   * Returns Git remotes defined in `.git/config`.
   *
   * Remote is returned with all transformations (such as `pushUrl, url.<base>.insteadOf`) already applied to it.
   * See [GitRemote] for details.
   *
   * **Note:** remotes can be defined separately in `.git/remotes` directory, by creating a file for each remote with
   * remote parameters written in the file. This method returns ONLY remotes defined in `.git/config`.
   * @return Git remotes defined in `.git/config`.
   */
  @VisibleForTesting
  fun parseRemotes(): Set<GitRemote> =
    remotes.asSequence()
      .filter { remote -> remote.urls.isNotEmpty() }
      .map { remote -> convertRemoteToGitRemote(urls, remote) }
      .toSet()

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

  private class UrlsAndPushUrls(
    val urls: List<String>,
    val pushUrls: List<String>,
  )

  private data class Remote(
    val name: String,
    val fetchSpecs: List<String>,
    val pushSpec: List<String>,
    val urls: List<String>,
    val pushUrls: List<String>,
  )

  private data class Url(
    val name: String,
    // null means no entry, i.e. nothing to substitute. Empty string means substituting everything
    val insteadOf: String?,
    val pushInsteadOf: String?,
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

    private fun convertRemoteToGitRemote(urls: List<Url>, remote: Remote): GitRemote {
      val substitutedUrls: UrlsAndPushUrls = substituteUrls(urls, remote)
      return GitRemote(remote.name, substitutedUrls.urls, substitutedUrls.pushUrls,
                       remote.fetchSpecs, remote.pushSpec)
    }

    /**
     * Creates an instance of GitConfig by reading information from the specified `.git/config` file.
     *
     * If some section is invalid, it is skipped, and a warning is reported.
     */
    @JvmStatic
    @VisibleForTesting
    fun read(project: Project?, root: Path): GitConfig {
      val configurations: Map<String, List<String>>
      try {
        configurations = GitConfigUtil.getValues(project, root, null)
      }
      catch (_: VcsException) {
        return GitConfig(listOf(), listOf(), listOf(), Core(null))
      }

      val remotes = mutableListOf<Remote>()
      val urls = mutableListOf<Url>()
      val trackedInfos = mutableListOf<BranchConfig>()
      val core = createCore(configurations)

      val sections = parseKeysIntoSections(configurations)

      for (section in sections) {
        val sectionName = section.first
        val sectionVariable = section.second

        when (sectionName) {
          SVN_REMOTE_SECTION, GIT_REMOTE_SECTION -> if (sectionVariable != null) {
            remotes.add(createRemote(configurations, sectionName, sectionVariable))
          }
          URL_SECTION -> if (sectionVariable != null) {
            urls.add(createUrl(configurations, sectionName, sectionVariable))
          }
          BRANCH_INFO_SECTION -> if (sectionVariable == null) {
            LOG.debug(
              String.format("Common branch option(s) defined .git/config. sectionName: %s%n section: %s", sectionName, section))
          }
          else {
            trackedInfos.add(createBranchConfig(configurations, sectionName, sectionVariable))
          }
        }
      }

      return GitConfig(remotes, urls, trackedInfos, core)
    }

    private fun parseKeysIntoSections(
      configurations: Map<String, List<String>>,
    ): Set<Pair<String, String?>> =
      configurations.entries.mapNotNull { entry ->
        val key: String = entry.key

        val variableSeparatorIndex = key.lastIndexOf('.')
        if (variableSeparatorIndex == -1) return@mapNotNull null

        val section = key.take(variableSeparatorIndex)
        val sectionNameSeparatorIndex = section.indexOf('.')

        if (sectionNameSeparatorIndex == -1) {
          section to null
        }
        else {
          val sectionName = section.take(sectionNameSeparatorIndex)
          val sectionVariable = section.substring(sectionNameSeparatorIndex + 1)

          sectionName to sectionVariable
        }
      }.toSet()

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

    /**
     * Applies `url.<base>.insteadOf` and `url.<base>.pushInsteadOf` transformations to `url` and `pushUrl` of
     * the given remote.
     *
     * The logic, is as follows:
     *
     *  * If remote.url starts with url.insteadOf, it it substituted.
     *  * If remote.pushUrl starts with url.insteadOf, it is substituted.
     *  * If remote.pushUrl starts with url.pushInsteadOf, it is not substituted.
     *  * If remote.url starts with url.pushInsteadOf, but remote.pushUrl is given, additional push url is not added.
     *
     * TODO: if there are several matches in url sections, the longest should be applied. // currently only one is applied
     *
     * This is according to `man git-config ("url.<base>.insteadOf" and "url.<base>.pushInsteadOf" sections`,
     * `man git-push ("URLS" section)` and the following discussions in the Git mailing list:
     * [insteadOf override urls and pushUrls](http://article.gmane.org/gmane.comp.version-control.git/183587),
     * [pushInsteadOf doesn't override explicit pushUrl](http://thread.gmane.org/gmane.comp.version-control.git/127910).
     */
    private fun substituteUrls(urlSections: List<Url>, remote: Remote): UrlsAndPushUrls {
      val urls: MutableList<String> = ArrayList(remote.urls.size)
      var pushUrls: MutableList<String> = mutableListOf()

      // urls are substituted by insteadOf
      // if there are no pushUrls, we create a pushUrl for pushInsteadOf substitutions
      for (remoteUrl in remote.urls) {
        var substituted = false
        for (url in urlSections) {
          val insteadOf = url.insteadOf
          val pushInsteadOf = url.pushInsteadOf
          // null means no entry, i.e. nothing to substitute. Empty string means substituting everything
          if (insteadOf != null && remoteUrl.startsWith(insteadOf)) {
            urls.add(substituteUrl(remoteUrl, url, insteadOf))
            substituted = true
            break
          }
          else if (pushInsteadOf != null && remoteUrl.startsWith(pushInsteadOf)) {
            if (remote.pushUrls.isEmpty()) { // only if there are no explicit pushUrls
              pushUrls.add(substituteUrl(remoteUrl, url, pushInsteadOf)) // pushUrl is different
            }
            urls.add(remoteUrl) // but url is left intact
            substituted = true
            break
          }
        }
        if (!substituted) {
          urls.add(remoteUrl)
        }
      }

      // pushUrls are substituted only by insteadOf, not by pushInsteadOf
      for (remotePushUrl in remote.pushUrls) {
        var substituted = false
        for (url in urlSections) {
          val insteadOf = url.insteadOf
          // null means no entry, i.e. nothing to substitute. Empty string means substituting everything
          if (insteadOf != null && remotePushUrl.startsWith(insteadOf)) {
            pushUrls.add(substituteUrl(remotePushUrl, url, insteadOf))
            substituted = true
            break
          }
        }
        if (!substituted) {
          pushUrls.add(remotePushUrl)
        }
      }

      // if no pushUrls are explicitly defined yet via pushUrl or url.<base>.pushInsteadOf, they are the same as urls.
      if (pushUrls.isEmpty()) {
        pushUrls = urls.toMutableList()
      }

      return UrlsAndPushUrls(urls, pushUrls)
    }

    private fun substituteUrl(remoteUrl: String, url: Url, insteadOf: String): String {
      return url.name + remoteUrl.substring(insteadOf.length)
    }

    private fun createBranchConfig(
      configurations: Map<String, List<String>>,
      sectionName: String,
      branchName: String,
    ): BranchConfig {
      val sectionKey = "$sectionName.$branchName"

      val remote: String? = getOneConfig(configurations, "$sectionKey.remote")
      val merge: String? = getOneConfig(configurations, "$sectionKey.merge")
      val rebase: String? = getOneConfig(configurations, "$sectionKey.rebase")

      return BranchConfig(branchName, remote, merge, rebase)
    }

    private fun createRemote(
      configurations: Map<String, List<String>>,
      sectionName: String,
      remoteName: String,
    ): Remote {
      val sectionKey = sectionName + "." + remoteName

      val fetchSpecs = getAllConfigs(configurations, sectionKey + ".fetch")
      val pushSpecs = getAllConfigs(configurations, sectionKey + ".push")
      val urls = getAllConfigs(configurations, sectionKey + ".url")
      val pushUrls = getAllConfigs(configurations, sectionKey + ".pushurl")

      return Remote(remoteName, fetchSpecs, pushSpecs, urls, pushUrls)
    }

    private fun createUrl(
      configurations: Map<String, List<String>>,
      sectionName: String,
      url: String,
    ): Url {
      val sectionKey = sectionName + "." + url

      val insteadof: String? = getOneConfig(configurations, sectionKey + ".insteadof")
      val pushInsteadof: String? = getOneConfig(configurations, sectionKey + ".pushinsteadof")

      return Url(url, insteadof, pushInsteadof)
    }

    private fun createCore(
      configurations: Map<String, List<String>>,
    ): Core {
      val hooksPath: String? = getOneConfig(configurations, CORE_SECTION + ".hookspath")

      return Core(hooksPath)
    }

    private fun getAllConfigs(
      configurations: Map<String, List<String>>,
      sectionKey: String,
    ): List<String> {
      return configurations[sectionKey] ?: emptyList()
    }

    private fun getOneConfig(
      configurations: Map<String, List<String>>,
      key: String,
    ): String? = configurations[key]?.lastOrNull()
  }
}
