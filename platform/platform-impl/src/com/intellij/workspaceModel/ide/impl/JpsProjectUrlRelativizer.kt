// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project

/**
 * The JpsProjectUrlRelativizer class is used to generate relative paths specific
 * to the project, mainly used by [com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl]
 * for cache serialization.
 *
 * This class generates base path for PROJECT_DIR. Additionally, since it inherits from
 * ApplicationLevelUrlRelativizer, its base paths (such as USER_HOME and
 * APPLICATION_HOME_DIR) are also generated.
 *
 * @param project The project for which the base paths are calculated.
 *                If the project is default project, then no base paths will be added.
 */
class JpsProjectUrlRelativizer(project: Project) : ApplicationLevelUrlRelativizer() {

  init {
    project.basePath?.let {
      val projectPath = it
      addBasePathWithProtocols("PROJECT_DIR", projectPath)
      // TODO add base paths for MAVEN_REPOSITORY and GRADLE_REPOSITORY
    }
  }

}

