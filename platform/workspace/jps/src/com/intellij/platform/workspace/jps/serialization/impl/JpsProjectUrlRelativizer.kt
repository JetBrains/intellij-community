// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
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
 * @param projectBaseDirPath path to the base directory of the project for which the base paths are calculated.
 */
@ApiStatus.Internal
class JpsProjectUrlRelativizer(private val projectBaseDirPath: String?, insideIdeProcess: Boolean) : ApplicationLevelUrlRelativizer(insideIdeProcess) {

  init {
    getBasePathsToAdd().forEach { pair ->
      val identifier = pair.first
      val url = pair.second
      url?.let { addBasePathWithProtocols(identifier, it) }
    }
  }

  private fun getBasePathsToAdd() = listOf(
    Pair("PROJECT_DIR", projectBaseDirPath),
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

