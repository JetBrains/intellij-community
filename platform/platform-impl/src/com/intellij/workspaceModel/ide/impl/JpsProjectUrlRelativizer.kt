// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import java.io.File

/**
 * The JpsProjectUrlRelativizer class is used to generate relative paths specific
 * to the project, mainly used by [com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl]
 * for cache serialization.
 *
 * This class generates base paths for PROJECT_DIR, MAVEN_REPOSITORY and GRADLE_REPOSITORY if they
 * can be calculated.
 * Additionally, since it inherits from ApplicationLevelUrlRelativizer, its base paths
 * (such as USER_HOME and APPLICATION_HOME_DIR) are also generated.
 *
 * @param project The project for which the base paths are calculated.
 */
class JpsProjectUrlRelativizer(private val project: Project) : ApplicationLevelUrlRelativizer() {

  init {
    getBasePathsToAdd().forEach { pair ->
      val identifier = pair.first
      val url = pair.second
      url?.let { addBasePathWithProtocols(identifier, it) }
    }
  }

  private fun getBasePathsToAdd() = listOf(
    Pair("PROJECT_DIR", project.basePath),
    Pair("MAVEN_REPOSITORY", JpsMavenSettings.getMavenRepositoryPath()),
    Pair("GRADLE_REPOSITORY", getGradleRepositoryPath())
  )

  private fun getGradleRepositoryPath(): String? {
    val gradleUserHome = System.getenv("GRADLE_USER_HOME") ?: (SystemProperties.getUserHome() + File.separator + ".gradle")

    return if (FileUtil.exists(gradleUserHome)) {
      gradleUserHome
    }
    else null
  }

}

