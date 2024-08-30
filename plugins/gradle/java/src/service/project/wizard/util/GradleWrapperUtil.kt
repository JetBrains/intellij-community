// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleWrapperUtil")

package org.jetbrains.plugins.gradle.service.project.wizard.util

import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.starters.local.generator.AssetsProcessor
import com.intellij.openapi.util.io.findOrCreateFile
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path

@ApiStatus.Internal
fun generateGradleWrapper(root: Path, gradleVersion: GradleVersion) {
  val configuration = GradleUtil.generateGradleWrapperConfiguration(gradleVersion)
  val propertiesLocation = StandardAssetsProvider().gradleWrapperPropertiesLocation
  val propertiesFile = root.findOrCreateFile(propertiesLocation)
  GradleUtil.writeWrapperConfiguration(propertiesFile, configuration)
  val assets = StandardAssetsProvider().getGradlewAssets()
  AssetsProcessor.getInstance().generateSources(root, assets, emptyMap())
}
