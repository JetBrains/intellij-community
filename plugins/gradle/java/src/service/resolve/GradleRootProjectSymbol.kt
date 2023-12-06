// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.platform.backend.presentation.TargetPresentation
import icons.GradleIcons
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.util.GradleBundle

@Internal
class GradleRootProjectSymbol(rootProjectPath: String) : GradleProjectSymbol(rootProjectPath) {

  override val projectName: String get() = rootProjectName

  override val qualifiedName: String get() = rootProjectName

  override fun presentation(): TargetPresentation = rootProjectPresentation

  override fun externalProject(rootProject: ExternalProject): ExternalProject = rootProject

  companion object {

    const val rootProjectName = ":"

    private val rootProjectPresentation: TargetPresentation = TargetPresentation
      .builder(GradleBundle.message("gradle.root.project"))
      .icon(GradleIcons.Gradle)
      .presentation()
  }
}
