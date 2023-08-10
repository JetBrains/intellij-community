// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleWrapperUtil")

package org.jetbrains.plugins.gradle.service.project.wizard.util

import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.starters.local.generator.AssetsProcessor
import com.intellij.openapi.util.io.findOrCreateFile
import org.gradle.util.GradleVersion
import org.gradle.wrapper.WrapperConfiguration
import org.gradle.wrapper.WrapperExecutor.*
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.Path
import java.util.*
import kotlin.io.path.writeText

@ApiStatus.Internal
fun generateGradleWrapper(root: Path, gradleVersion: GradleVersion) {
  generateGradleWrapper(root, generateGradleWrapperConfiguration(gradleVersion))
}

private fun generateGradleWrapper(root: Path, configuration: WrapperConfiguration) {
  val propertiesLocation = StandardAssetsProvider().gradleWrapperPropertiesLocation
  val propertiesFile = root.findOrCreateFile(propertiesLocation)
  val propertiesContent = getWrapperPropertiesContent(configuration)
  propertiesFile.writeText(propertiesContent)
  val assets = StandardAssetsProvider().getGradlewAssets()
  AssetsProcessor.getInstance().generateSources(root, assets, emptyMap())
}

private fun generateGradleWrapperConfiguration(gradleVersion: GradleVersion): WrapperConfiguration {
  return WrapperConfiguration().apply {
    val distributionSource = if (gradleVersion.isSnapshot) "distributions-snapshots" else "distributions"
    distribution = URI("https://services.gradle.org/$distributionSource/gradle-${gradleVersion.version}-bin.zip")
  }
}

private fun getWrapperPropertiesContent(configuration: WrapperConfiguration): String {
  val properties = getWrapperProperties(configuration)
  val output = ByteArrayOutputStream()
  properties.store(output, null)
  return output.toString()
}

private fun getWrapperProperties(configuration: WrapperConfiguration): Properties {
  return Properties().apply {
    setProperty(DISTRIBUTION_URL_PROPERTY, configuration.distribution.toString())
    setProperty(DISTRIBUTION_BASE_PROPERTY, configuration.distributionBase)
    setProperty(DISTRIBUTION_PATH_PROPERTY, configuration.distributionPath)
    setProperty(ZIP_STORE_BASE_PROPERTY, configuration.zipBase)
    setProperty(ZIP_STORE_PATH_PROPERTY, configuration.zipPath)
  }
}
