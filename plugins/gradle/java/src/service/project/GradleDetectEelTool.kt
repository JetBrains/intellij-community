// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel
import java.nio.file.Path

// Heuristics to detect eel this Gradle project sits on.
// Note, that IdeaProject and IdeaModule are Gradle settings here, not IJ entities

internal fun detectEel(ideaProject: IdeaProject): EelDescriptor? =
  ideaProject.modules.firstNotNullOfOrNull { detectEel(it, null) }

internal fun detectEel(ideaModule: IdeaModule, gradleModel: GradleSourceSetModel): EelDescriptor? =
  detectEel(ideaModule, gradleModel.sourceSets.values.firstOrNull()?.sources?.values?.firstOrNull()?.srcDirs?.firstOrNull()?.toPath())


internal fun detectEel(ideaModule: IdeaModule, gradleModel: ExternalSourceSet): EelDescriptor? =
  detectEel(ideaModule, gradleModel.sources.values.firstOrNull()?.srcDirs?.firstOrNull()?.toPath())


private fun detectEel(ideaModule: IdeaModule, fallbackPath: Path?): EelDescriptor? =
  (ideaModule.contentRoots?.all?.firstOrNull()?.rootDirectory?.toPath() ?: fallbackPath)?.getEelDescriptor()
