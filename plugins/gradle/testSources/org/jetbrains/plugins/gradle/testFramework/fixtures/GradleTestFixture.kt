// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.IdeaTestFixture

interface GradleTestFixture : IdeaTestFixture {

  suspend fun openProject(relativePath: String, numProjectSyncs: Int = 1): Project

  suspend fun linkProject(project: Project, relativePath: String)

  suspend fun reloadProject(project: Project, relativePath: String = ".", configure: ImportSpecBuilder.() -> Unit = {})

  suspend fun awaitOpenProjectConfiguration(numProjectSyncs: Int = 1, openProject: suspend () -> Project): Project

  suspend fun <R> awaitProjectConfiguration(project: Project, numProjectSyncs: Int = 1, action: suspend () -> R): R

  fun assertNotificationIsVisible(project: Project, isNotificationVisible: Boolean)
}