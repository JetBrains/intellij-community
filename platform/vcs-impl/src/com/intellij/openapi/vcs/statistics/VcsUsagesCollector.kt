// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newCounterMetric
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.util.text.nullize
import com.intellij.vcsUtil.VcsUtil
import java.util.*

class VcsUsagesCollector : ProjectUsagesCollector() {
  override fun getGroupId(): String = "vcs.configuration"
  override fun getVersion(): Int = 2

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val set = HashSet<MetricEvent>()

    val vcsManager = ProjectLevelVcsManagerEx.getInstanceEx(project)
    val clm = ChangeListManagerImpl.getInstanceImpl(project)
    val projectBaseDir = project.basePath?.let { VcsUtil.getVirtualFile(it) }

    for (vcs in vcsManager.allActiveVcss) {
      val pluginInfo = getPluginInfo(vcs.javaClass)

      val metric = newMetric("active.vcs")
      metric.data.addPluginInfo(pluginInfo)
      metric.data.addData("vcs", vcs.name)
      set.add(metric)
    }

    for (mapping in vcsManager.directoryMappings) {
      val vcsName = mapping.vcs.nullize(true)
      val vcs = vcsManager.findVcsByName(vcsName)
      val pluginInfo = vcs?.let { getPluginInfo(it.javaClass) }

      val metric = newMetric("mapping")
      metric.data.addPluginInfo(pluginInfo)
      metric.data.addData("vcs", vcsName ?: "None")
      metric.data.addData("is_project_mapping", mapping.isDefaultMapping)
      if (!mapping.isDefaultMapping) {
        metric.data.addData("is_base_dir", projectBaseDir != null &&
                                           projectBaseDir == VcsUtil.getVirtualFile(mapping.directory))
      }
      set.add(metric)
    }

    val defaultVcs = vcsManager.findVcsByName(vcsManager.haveDefaultMapping())
    if (defaultVcs != null) {
      val pluginInfo = getPluginInfo(defaultVcs.javaClass)

      val explicitRoots = vcsManager.directoryMappings
        .filter { it.vcs == defaultVcs.name }
        .filter { it.directory.isNotEmpty() }
        .map { VcsUtil.getVirtualFile(it.directory) }
        .toSet()

      val projectMappedRoots = vcsManager.allVcsRoots
        .filter { it.vcs == defaultVcs }
        .filter { it.path != null && !explicitRoots.contains(it.path) }

      for (vcsRoot in projectMappedRoots) {
        val metric = newMetric("project.mapped.root")
        metric.data.addPluginInfo(pluginInfo)
        metric.data.addData("vcs", defaultVcs.name)
        metric.data.addData("is_base_dir", vcsRoot.path == projectBaseDir)
        set.add(metric)
      }
    }

    set.add(newCounterMetric("mapped.roots", vcsManager.allVcsRoots.size))
    set.add(newCounterMetric("changelists", clm.changeListsNumber))
    set.add(newCounterMetric("unversioned.files", clm.unversionedFiles.size))
    set.add(newCounterMetric("ignored.files", clm.ignoredFiles.size))

    return set
  }
}
