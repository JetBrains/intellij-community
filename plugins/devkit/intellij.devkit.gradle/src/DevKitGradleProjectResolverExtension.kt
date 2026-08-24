// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.devkit.gradle.tooling.IntelliJPlatformAuxiliaryArtifactProvider
import com.intellij.devkit.gradle.tooling.IntelliJPlatformGradleModel
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.gradle.toolingExtension.modelProvider.GradleClassProjectModelProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

/**
 * Fetches IntelliJ Platform metadata before task execution, allowing the IDE to retain it when later sync stages fail.
 */
internal class DevKitGradleProjectResolverExtension : AbstractProjectResolverExtension() {
  override fun getToolingExtensionsClasses(): Set<Class<*>> =
    setOf(IntelliJPlatformAuxiliaryArtifactProvider::class.java)

  override fun getModelProviders() = listOf(
    GradleClassProjectModelProvider(
      IntelliJPlatformGradleModel::class.java,
      GradleModelFetchPhase.PROJECT_LOADED_PHASE,
    )
  )
}
