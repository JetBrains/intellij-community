package com.intellij.cce.java.project

import com.intellij.cce.core.Language
import com.intellij.cce.java.test.isGradle
import com.intellij.cce.java.test.isMaven
import com.intellij.cce.project.ProjectSyncInvoker
import com.intellij.cce.project.ProjectSyncInvokerParams
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.Observation
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.plugins.gradle.util.GradleConstants

internal val LOG = fileLogger()

internal class JavaProjectSyncInvoker : ProjectSyncInvoker {
  override fun isApplicable(params: ProjectSyncInvokerParams): Boolean =
    params.language == Language.JAVA ||
    params.language == Language.KOTLIN

  override suspend fun syncProject(project: Project) {
    LOG.info("Sync project")
    val projectSystemId = when {
      isMaven(project) -> MavenUtil.SYSTEM_ID
      isGradle(project) -> GradleConstants.SYSTEM_ID
      else -> null
    }

    if (projectSystemId == null) {
      LOG.warn("Can't detect project system ID. Skip syncing")
      return
    }

    val path = project.basePath
    if (path == null) {
      LOG.warn("Project path is null. Skip syncing")
      return
    }

    val tool = ExternalSystemUnlinkedProjectAware.getInstance(projectSystemId)
    if (tool == null) {
      LOG.warn("Can't find service. Skip syncing")
      return
    }

    LOG.info("Unlink project")
    tool.unlinkProject(project, path)
    Observation.awaitConfiguration(project)

    LOG.info("Link project back")
    tool.linkAndLoadProjectAsync(project, path)
    Observation.awaitConfiguration(project)

    LOG.info("Project syncing completed")
  }
}
