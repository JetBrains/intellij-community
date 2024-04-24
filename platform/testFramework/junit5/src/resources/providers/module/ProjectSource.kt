// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.providers.module

import com.intellij.openapi.project.Project

sealed class ProjectSource {
  class ExplicitProject(internal val project: Project) : ProjectSource()

  /**
   * Detect a project created by [com.intellij.testFramework.junit5.resources.providers.project.ProjectProvider]
   * To use it create an extension with this provider or use [com.intellij.testFramework.junit5.resources.ProjectExtension]
   * or [com.intellij.testFramework.junit5.resources.ProjectResource]
   */
  data object ProjectFromExtension : ProjectSource()
}