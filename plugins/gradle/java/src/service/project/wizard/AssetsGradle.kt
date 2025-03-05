// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorFile
import com.intellij.ide.starters.local.StandardAssetsProvider
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.GradleUtil

object AssetsGradle {

  fun getGradleWrapperAssets(gradleVersion: GradleVersion): List<GeneratorAsset> {
    return StandardAssetsProvider().getGradlewAssets() +
           getGradleWrapperPropertiesAsset(gradleVersion)
  }

  private fun getGradleWrapperPropertiesAsset(gradleVersion: GradleVersion): GeneratorAsset {
    val propertiesLocation = StandardAssetsProvider().gradleWrapperPropertiesLocation
    val configuration = GradleUtil.generateGradleWrapperConfiguration(gradleVersion)
    val byteArray = GradleUtil.writeWrapperConfigurationToByteArray(configuration)
    return GeneratorFile(propertiesLocation, byteArray)
  }
}

fun AssetsNewProjectWizardStep.addGradleGitIgnoreAsset() {
  addAssets(StandardAssetsProvider().getGradleIgnoreAssets())
}

fun AssetsNewProjectWizardStep.addGradleWrapperAsset(gradleVersion: GradleVersion) {
  addAssets(AssetsGradle.getGradleWrapperAssets(gradleVersion))
}