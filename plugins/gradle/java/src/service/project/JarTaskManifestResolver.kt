// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.externalSystem.JarTaskManifestData
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.model.jar.JarTaskManifestConfiguration
import org.jetbrains.plugins.gradle.tooling.internal.jar.JarTaskManifestConfigurationImpl

class JarTaskManifestResolver : AbstractProjectResolverExtension() {
  override fun getExtraProjectModelClasses() = setOf(JarTaskManifestConfiguration::class.java)

  override fun getToolingExtensionsClasses() = setOf(JarTaskManifestConfigurationImpl::class.java)

  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    doPopulate(gradleModule, ideModule)
    super.populateModuleExtraModels(gradleModule, ideModule)
  }

  private fun doPopulate(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    val model = resolverCtx.getProjectModel(gradleModule, JarTaskManifestConfiguration::class.java) ?: return
    val manifestData = JarTaskManifestData(model.manifestAttributes)
    ideModule.createChild(JarTaskManifestData.KEY, manifestData)
  }
}