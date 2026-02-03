// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.externalSystem.JavaManifestData
import com.intellij.gradle.toolingExtension.impl.javaModel.manifestModel.JavaGradleManifestModelBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.javaModel.JavaGradleManifestModel

@ApiStatus.Internal
class JavaGradleManifestResolver : AbstractProjectResolverExtension() {

  override fun getExtraProjectModelClasses() = setOf(JavaGradleManifestModel::class.java)

  override fun getToolingExtensionsClasses() = setOf(JavaGradleManifestModelBuilder::class.java)

  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    doPopulate(gradleModule, ideModule)
    super.populateModuleExtraModels(gradleModule, ideModule)
  }

  private fun doPopulate(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    val model = resolverCtx.getProjectModel(gradleModule, JavaGradleManifestModel::class.java) ?: return
    val manifestData = JavaManifestData(model.manifestAttributes)
    ideModule.createChild(JavaManifestData.KEY, manifestData)
  }
}