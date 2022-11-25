// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.starters.local.generator.AssetsProcessor
import com.intellij.openapi.vfs.createFile
import com.intellij.openapi.externalSystem.util.runWriteActionAndWait
import com.intellij.openapi.externalSystem.util.text
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion
import org.gradle.wrapper.WrapperConfiguration
import org.gradle.wrapper.WrapperExecutor.*
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.*

fun generateWrapper(root: VirtualFile, gradleVersion: GradleVersion) {
  generateWrapper(root, defaultWrapperConfiguration(gradleVersion))
}

fun generateWrapper(root: VirtualFile, configuration: WrapperConfiguration) {
  runWriteActionAndWait {
    val wrapperProperties = root.createFile(StandardAssetsProvider().gradleWrapperPropertiesLocation)
    wrapperProperties.text = getWrapperPropertiesContent(configuration)
    val assets = StandardAssetsProvider().getGradlewAssets()
    AssetsProcessor.generateSources(root, assets, emptyMap())
  }
}

private fun defaultWrapperConfiguration(gradleVersion: GradleVersion): WrapperConfiguration {
  return WrapperConfiguration().apply {
    distribution = URI("https://services.gradle.org/distributions/gradle-${gradleVersion.version}-bin.zip")
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
