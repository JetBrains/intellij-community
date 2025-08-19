// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchUtil
import org.ini4j.Ini
import org.ini4j.Profile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern

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

    private val REMOTE_SECTION: Pattern = Pattern.compile("(?:svn-)?remote \"(.*)\"", Pattern.CASE_INSENSITIVE)
    private val URL_SECTION: Pattern = Pattern.compile("url \"(.*)\"", Pattern.CASE_INSENSITIVE)
    private val BRANCH_INFO_SECTION: Pattern = Pattern.compile("branch \"(.*)\"", Pattern.CASE_INSENSITIVE)
    private val BRANCH_COMMON_PARAMS_SECTION: Pattern = Pattern.compile("branch", Pattern.CASE_INSENSITIVE)
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
    fun read(configFile: File): GitConfig {
      val emptyConfig = GitConfig(mutableListOf(), mutableListOf(), mutableListOf(), Core(null))
      if (!configFile.exists() || configFile.isDirectory()) {
        LOG.info("No .git/config file at " + configFile.path)
        return emptyConfig
      }

      val ini: Ini?
      try {
        ini = loadIniFile(configFile)
      }
      catch (e: IOException) {
        LOG.warn("Couldn't read .git/config at" + configFile.path, e)
        return emptyConfig
      }

      val (remotes, urls) = parseRemotes(ini)
      val trackedInfos = parseTrackedInfos(ini)
      val core: Core = parseCore(ini)

      return GitConfig(remotes, urls, trackedInfos, core)
    }

    private fun parseTrackedInfos(ini: Ini): List<BranchConfig> {
      val configs: MutableList<BranchConfig> = ArrayList()
      for (stringSectionEntry in ini.entries) {
        val sectionName = stringSectionEntry.key
        val section = stringSectionEntry.value

        val branchConfig: BranchConfig? = parseBranchSection(sectionName, section)
        if (branchConfig != null) {
          configs.add(branchConfig)
        }
      }
      return configs
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

    private fun parseBranchSection(
      sectionName: String,
      section: Profile.Section,
    ): BranchConfig? {
      val matcher: Matcher = BRANCH_INFO_SECTION.matcher(sectionName)
      if (matcher.matches()) {
        val remote = section["remote"]
        val merge = section["merge"]
        val rebase = section["rebase"]
        return BranchConfig(matcher.group(1), remote, merge, rebase)
      }
      if (BRANCH_COMMON_PARAMS_SECTION.matcher(sectionName).matches()) {
        LOG.debug(String.format("Common branch option(s) defined .git/config. sectionName: %s%n section: %s", sectionName, section))
        return null
      }
      return null
    }

    private fun parseRemotes(ini: Ini): Pair<List<Remote>, List<Url>> {
      val remotes: MutableList<Remote> = ArrayList()
      val urls: MutableList<Url> = ArrayList()
      for (sectionName in ini.keys) {
        val section: Profile.Section = ini[sectionName]!!

        val remote: Remote? = parseRemoteSection(sectionName, section)
        if (remote != null) {
          remotes.add(remote)
        }
        else {
          val url: Url? = parseUrlSection(sectionName, section)
          if (url != null) {
            urls.add(url)
          }
        }
      }
      return remotes to urls
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

    private fun parseRemoteSection(
      sectionName: String,
      section: Profile.Section,
    ): Remote? {
      val matcher: Matcher = REMOTE_SECTION.matcher(sectionName)
      if (matcher.matches() && matcher.groupCount() == 1) {
        val fetch = section.getAll("fetch") ?: emptyList()
        val push = section.getAll("push") ?: emptyList()
        val url = section.getAll("url") ?: emptyList()
        val pushurl = section.getAll("pushurl") ?: emptyList()
        return Remote(matcher.group(1), fetch, push, url, pushurl)
      }
      return null
    }

    private fun parseUrlSection(sectionName: String, section: Profile.Section): Url? {
      val matcher: Matcher = URL_SECTION.matcher(sectionName)
      if (matcher.matches() && matcher.groupCount() == 1) {
        val insteadof = section["insteadof"]
        val pushInsteadof = section["pushinsteadof"]
        return Url(matcher.group(1), insteadof, pushInsteadof)
      }
      return null
    }

    private fun parseCore(ini: Ini): Core {
      var hooksPath: String? = null

      val sections = ini.getAll(CORE_SECTION) ?: emptyList()
      for (section in ContainerUtil.reverse(sections)) { // take entry from last section for duplicates
        if (hooksPath == null) hooksPath = section.getAll("hookspath")?.lastOrNull()
      }
      return Core(hooksPath)
    }
  }
}
