// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectInfo

interface GradleProjectInfo {

  val projectName: String

  val projectRelativePath: String

  val rootModule: GradleModuleInfo

  val modules: List<GradleModuleInfo>

  val composites: List<GradleProjectInfo>
}
