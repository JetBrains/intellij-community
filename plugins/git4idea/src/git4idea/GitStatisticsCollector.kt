// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.google.common.collect.HashMultiset
import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getBooleanUsage
import com.intellij.internal.statistic.utils.getCountingUsage
import com.intellij.openapi.project.Project
import com.intellij.util.io.URLUtil
import git4idea.config.GitVcsSettings
import git4idea.config.GitVersion
import git4idea.repo.GitRemote

class GitStatisticsCollector : ProjectUsagesCollector() {

  override fun getUsages(project: Project): Set<UsageDescriptor> {
    val repositoryManager = GitUtil.getRepositoryManager(project)
    val settings = GitVcsSettings.getInstance(project)
    val repositories = repositoryManager.repositories
    val usages = hashSetOf<UsageDescriptor>()

    usages.add(UsageDescriptor("config.repo.sync." + settings.syncSetting.name, 1))
    usages.add(UsageDescriptor("config.update.type." + settings.updateType.name, 1))
    usages.add(UsageDescriptor("config.save.policy." + settings.updateChangesPolicy().name, 1))
    usages.add(getBooleanUsage("config.ssh", settings.isIdeaSsh))

    usages.add(getBooleanUsage("config.push.autoupdate", settings.autoUpdateIfPushRejected()))
    usages.add(getBooleanUsage("config.push.update.all.roots", settings.shouldUpdateAllRootsIfPushRejected()))
    usages.add(getBooleanUsage("config.cherry-pick.autocommit", settings.isAutoCommitOnCherryPick))
    usages.add(getBooleanUsage("config.warn.about.crlf", settings.warnAboutCrlf()))
    usages.add(getBooleanUsage("config.warn.about.detached", settings.warnAboutDetachedHead()))

    usages.add(versionUsage(GitVcs.getInstance(project).version))

    for (repository in repositories) {
      val branches = repository.branches
      usages.add(getCountingUsage("data.local.branches.count", branches.localBranches.size, listOf(0, 1, 2, 5, 8, 15, 30, 50)))
      usages.add(getCountingUsage("data.remote.branches.count", branches.remoteBranches.size, listOf(0, 1, 2, 5, 8, 15, 30, 100)))
      usages.add(getCountingUsage("data.remotes.in.project", repository.remotes.size, listOf(0, 1, 2, 5)))

      val servers = repository.remotes.mapNotNull(this::getRemoteServerType).toCollection(HashMultiset.create())
      for (serverName in servers) {
        usages.add(getCountingUsage("data.remote.servers." + serverName, servers.count(serverName), listOf(0, 1, 2, 3, 5)))
      }
    }

    return usages
  }

  private fun versionUsage(version: GitVersion) = UsageDescriptor("version.${version.semanticPresentation}")

  override fun getGroupId(): String {
    return "statistics.vcs.git.settings"
  }

  private fun getRemoteServerType(remote: GitRemote): String? {
    val hosts = remote.urls.map(URLUtil::parseHostFromSshUrl).distinct()

    if (hosts.contains("github.com")) return "github.com"
    if (hosts.contains("gitlab.com")) return "gitlab.com"
    if (hosts.contains("bitbucket.org")) return "bitbucket.org"

    if (remote.urls.any { it.contains("github") }) return "github.custom"
    if (remote.urls.any { it.contains("gitlab") }) return "gitlab.custom"
    if (remote.urls.any { it.contains("bitbucket") }) return "bitbucket.custom"

    return null
  }
}
