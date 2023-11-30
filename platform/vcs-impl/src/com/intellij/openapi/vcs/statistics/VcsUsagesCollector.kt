// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.statistics

import com.intellij.ide.impl.isTrusted
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.util.text.nullize
import com.intellij.vcsUtil.VcsUtil

internal class VcsUsagesCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    if (!project.isTrusted()) return emptySet()

    val set = HashSet<MetricEvent>()

    val vcsManager = ProjectLevelVcsManagerEx.getInstanceEx(project)
    val clm = ChangeListManager.getInstance(project)
    val projectBaseDir = project.basePath?.let { VcsUtil.getVirtualFile(it) }

    for (vcs in vcsManager.allActiveVcss) {
      val pluginInfo = getPluginInfo(vcs.javaClass)
      set.add(ACTIVE_VCS.metric(pluginInfo, vcs.name))
    }

    for (mapping in vcsManager.directoryMappings) {
      val vcsName = mapping.vcs.nullize(true)
      val vcs = vcsManager.findVcsByName(vcsName)
      val pluginInfo = vcs?.let { getPluginInfo(it.javaClass) }

      val data = mutableListOf<EventPair<*>>()
      data.add(EventFields.PluginInfo.with(pluginInfo))
      data.add(IS_PROJECT_MAPPING_FIELD.with(mapping.isDefaultMapping))
      data.add(VCS_FIELD_WITH_NONE.with(vcsName ?: "None"))
      if (!mapping.isDefaultMapping) {
        data.add(IS_BASE_DIR_FIELD.with(projectBaseDir != null &&
                                        projectBaseDir == VcsUtil.getVirtualFile(mapping.directory)))
      }
      set.add(MAPPING.metric(data))
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
        .filter { !explicitRoots.contains(it.path) }

      for (vcsRoot in projectMappedRoots) {
        set.add(PROJECT_MAPPED_ROOTS.metric(pluginInfo, defaultVcs.name, vcsRoot.path == projectBaseDir))
      }
    }

    set.add(MAPPED_ROOTS.metric(vcsManager.allVcsRoots.size))
    set.add(CHANGELISTS.metric(clm.changeListsNumber))
    set.add(UNVERSIONED_FILES.metric(clm.unversionedFilesPaths.size))
    set.add(IGNORED_FILES.metric(clm.ignoredFilePaths.size))

    return set
  }

  private val GROUP = EventLogGroup("vcs.configuration", 3)
  private val VCS_FIELD = EventFields.StringValidatedByEnum("vcs", "vcs")
  private val ACTIVE_VCS = GROUP.registerEvent("active.vcs", EventFields.PluginInfo,
                                               VCS_FIELD)
  private val IS_PROJECT_MAPPING_FIELD = EventFields.Boolean("is_project_mapping")
  private val IS_BASE_DIR_FIELD = EventFields.Boolean("is_base_dir")
  private val VCS_FIELD_WITH_NONE = object : StringEventField("vcs") {
    override val validationRule: List<String>
      get() = listOf("{enum#vcs}", "{enum:None}")
  }
  private val MAPPING = GROUP.registerVarargEvent(
    "mapping", EventFields.PluginInfo,
    VCS_FIELD_WITH_NONE, IS_PROJECT_MAPPING_FIELD, IS_BASE_DIR_FIELD
  )

  private val PROJECT_MAPPED_ROOTS = GROUP.registerEvent(
    "project.mapped.root", EventFields.PluginInfo,
    VCS_FIELD, EventFields.Boolean("is_base_dir")
  )
  private val MAPPED_ROOTS = GROUP.registerEvent("mapped.roots", EventFields.Count)
  private val CHANGELISTS = GROUP.registerEvent("changelists", EventFields.Count)
  private val UNVERSIONED_FILES = GROUP.registerEvent("unversioned.files", EventFields.Count)
  private val IGNORED_FILES = GROUP.registerEvent("ignored.files", EventFields.Count)
}
