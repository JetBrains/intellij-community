/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea

import com.intellij.internal.statistic.AbstractProjectsUsagesCollector
import com.intellij.internal.statistic.UsageTrigger
import com.intellij.internal.statistic.beans.GroupDescriptor
import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.utils.getBooleanUsage
import com.intellij.internal.statistic.utils.getCountingUsage
import com.intellij.openapi.project.Project
import git4idea.config.GitVcsSettings

fun reportUsage(key: String) {
  UsageTrigger.trigger(key)
}

class GitStatisticsCollector : AbstractProjectsUsagesCollector() {
  private val ID = GroupDescriptor.create("Git")

  override fun getProjectUsages(project: Project): Set<UsageDescriptor> {
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
    usages.add(getBooleanUsage("config.force.push", settings.warnAboutDetachedHead()))

    for (repository in repositories) {
      val branches = repository.branches
      usages.add(getCountingUsage("data.local.branches.count", branches.localBranches.size, listOf(0, 1, 2, 5, 8, 15, 30, 50)))
      usages.add(getCountingUsage("data.remote.branches.count", branches.remoteBranches.size, listOf(0, 1, 2, 5, 8, 15, 30, 100)))
      usages.add(getCountingUsage("data.remotes.in.project", repository.remotes.size, listOf(0, 1, 2, 5)))
    }

    return usages
  }

  override fun getGroupId(): GroupDescriptor {
    return ID
  }
}
