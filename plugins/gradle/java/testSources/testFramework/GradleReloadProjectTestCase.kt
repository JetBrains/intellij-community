// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class GradleReloadProjectTestCase : GradleReloadProjectBaseTestCase() {

  val project: Project get() = projectFixture.project
  val projectName: String get() = projectFixture.projectName
  val projectRoot: VirtualFile get() = projectFixture.projectRoot

  suspend fun reloadProject(configure: ImportSpecBuilder.() -> Unit = {}) {
    gradleFixture.reloadProject(project, projectName, configure)
  }
}