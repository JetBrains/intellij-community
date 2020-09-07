// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.model.presentation.SymbolPresentation
import icons.GradleIcons
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.util.GradleBundle

@Internal
class GradleRootProjectSymbol(rootProjectPath: String) : GradleProjectSymbol(rootProjectPath) {

  override val projectName: String get() = rootProjectName

  override val qualifiedName: String get() = rootProjectName

  override fun getSymbolPresentation(): SymbolPresentation = rootProjectPresentation

  override fun externalProject(rootProject: ExternalProject): ExternalProject? = rootProject

  companion object {

    const val rootProjectName = ":"

    private val rootProjectPresentation: SymbolPresentation = SymbolPresentation.create(
      GradleIcons.Gradle,
      rootProjectName,
      GradleBundle.message("gradle.root.project")
    )
  }
}
