// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleWrapperUtil")

package org.jetbrains.plugins.gradle.service.project.wizard.util

import com.intellij.ide.starters.local.generator.AssetsProcessor
import com.intellij.ide.starters.local.generator.convertOutputLocationForTests
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.gradle.service.project.wizard.AssetsGradle
import java.nio.file.Path

@ApiStatus.Internal
fun generateGradleWrapper(root: Path, gradleVersion: GradleVersion) {
  val assets = AssetsGradle.getGradleWrapperAssets(gradleVersion)
  AssetsProcessor.getInstance().generateSources(root, assets, emptyMap())
}

@TestOnly
@ApiStatus.Internal
fun generateGradleWrapper(root: VirtualFile, gradleVersion: GradleVersion) {
  generateGradleWrapper(convertOutputLocationForTests(root), gradleVersion)
}
