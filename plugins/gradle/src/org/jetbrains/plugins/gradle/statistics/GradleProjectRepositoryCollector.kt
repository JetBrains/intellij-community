// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.project.repository.FileRepositoryData
import com.intellij.openapi.externalSystem.model.project.repository.ProjectRepositoryData
import com.intellij.openapi.externalSystem.model.project.repository.UrlRepositoryData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.GradleConstants

@ApiStatus.Internal
internal class GradleProjectRepositoryCollector : ProjectUsagesCollector() {

  private enum class Type {
    MAVEN,
    IVY,
    FILE,
    OTHER
  }

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP: EventLogGroup = EventLogGroup("build.gradle.project.repositories", 1)

  private val MAVEN_REPOSITORY_COUNT: IntEventField = EventFields.Int("maven_repositories_count")
  private val IVY_REPOSITORIES_COUNT: IntEventField = EventFields.Int("ivy_repositories_count")
  private val FILE_REPOSITORIES_COUNT: IntEventField = EventFields.Int("file_repositories_count")
  private val OTHER_REPOSITORIES_COUNT: IntEventField = EventFields.Int("other_repositories_count")
  private val DECLARED_REPOSITORIES_EVENT: VarargEventId = GROUP.registerVarargEvent(
    "repository",
    MAVEN_REPOSITORY_COUNT,
    IVY_REPOSITORIES_COUNT,
    FILE_REPOSITORIES_COUNT,
    OTHER_REPOSITORIES_COUNT
  )

  override suspend fun collect(project: Project): Set<MetricEvent> {
    val uniqueRepositories = ModuleManager.getInstance(project).modules
      .mapNotNull { it.getDataNode() }
      .flatMap { it.getRepositories() }
      .distinct()
    val stats = uniqueRepositories
      .groupBy { it.asFusType() }
      .entries
      .map { it.asEventPair() }
      .toList()
    return setOf(DECLARED_REPOSITORIES_EVENT.metric(stats))
  }

  private fun Map.Entry<Type, List<Any>>.asEventPair(): EventPair<Int> {
    val field = when (key) {
      Type.MAVEN -> MAVEN_REPOSITORY_COUNT
      Type.IVY -> IVY_REPOSITORIES_COUNT
      Type.FILE -> FILE_REPOSITORIES_COUNT
      else -> OTHER_REPOSITORIES_COUNT
    }
    return field.with(value.size)
  }

  private fun ProjectRepositoryData.asFusType(): Type {
    return when (this) {
      is UrlRepositoryData -> when (type) {
        UrlRepositoryData.Type.MAVEN -> Type.MAVEN
        UrlRepositoryData.Type.IVY -> Type.IVY
        UrlRepositoryData.Type.OTHER -> Type.OTHER
      }
      is FileRepositoryData -> Type.FILE
      else -> Type.OTHER
    }
  }

  private fun Module.getDataNode(): DataNode<ProjectData>? {
    val path = ExternalSystemApiUtil.getExternalProjectPath(this) ?: return null
    return ExternalSystemApiUtil.findProjectNode(project, GradleConstants.SYSTEM_ID, path)
  }

  private fun DataNode<ProjectData>.getRepositories(): List<ProjectRepositoryData> {
    return ExternalSystemApiUtil.findAll(this, ProjectRepositoryData.KEY)
      .filter { it.data.owner == GradleConstants.SYSTEM_ID }
      .map { it.data }
      .toList()
  }
}