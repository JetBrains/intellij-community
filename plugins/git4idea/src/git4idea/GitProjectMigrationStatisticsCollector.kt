// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getBooleanUsage
import com.intellij.internal.statistic.utils.getCountingUsage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

class GitProjectMigrationStatisticsCollector : ProjectUsagesCollector() {

  override fun getUsages(project: Project): Set<UsageDescriptor> {
    val usages = hashSetOf<UsageDescriptor>()

    val gitVcs = GitVcs.getInstance(project)
    val manager = ProjectLevelVcsManager.getInstance(project)

    val baseDir = project.baseDir
    val actualGitRoots = manager.getRootsUnderVcs(gitVcs)
    val mappings = manager.directoryMappings.filter { it.vcs != null }

    val gitMappings = mappings.filter { it.vcs == gitVcs.name }
    val hasDefaultGitMapping = gitMappings.any { it.isDefaultMapping }
    val explicitlyMappedRoots = gitMappings.mapNotNull { LocalFileSystem.getInstance().findFileByPath(it.systemIndependentPath()) }
    val projectMappingRoots = actualGitRoots.toMutableSet() - explicitlyMappedRoots

    if (hasDefaultGitMapping) {
      usages.add(getBooleanUsage("base.dir.undefined", baseDir == null))
      usages.add(getBooleanUsage("has.default.git.mapping", hasDefaultGitMapping))
      usages.add(getBooleanUsage("has.other.vcses.mappings", mappings.size != gitMappings.size))
      usages.add(getCountingUsage("git.mappings.count", gitMappings.size))
      usages.add(getCountingUsage("git.detected.roots.count", actualGitRoots.size))
      usages.add(getCountingUsage("project.mapping.roots.count", projectMappingRoots.size))

      if (baseDir != null) {
        var prefix: String? = null
        var gitRoot: VirtualFile? = null

        if (gitMappings.size == 1 && actualGitRoots.size == 1) {
          prefix = "single.mapping"
          gitRoot = actualGitRoots.single()
        }
        else if (projectMappingRoots.size == 1) {
          prefix = "singe.base.mapping"
          gitRoot = projectMappingRoots.single()
        }

        if (gitRoot != null && prefix != null) {
          if (gitRoot == baseDir) {
            usages.add(UsageDescriptor("$prefix.is.base.dir", 1))
          }
          else if (VfsUtilCore.isAncestor(gitRoot, baseDir, false)) {
            usages.add(UsageDescriptor("$prefix.above.base.dir", 1))
          }
          else if (VfsUtilCore.isAncestor(baseDir, gitRoot, false)) {
            usages.add(UsageDescriptor("$prefix.under.base.dir", 1))
          }
        }
      }
    }

    return usages
  }

  override fun getGroupId(): String {
    return "statistics.vcs.git.project.root"
  }
}
