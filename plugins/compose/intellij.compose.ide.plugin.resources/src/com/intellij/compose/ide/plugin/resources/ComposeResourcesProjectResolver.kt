// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesExtension
import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesModel
import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesModelBuilder
import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesModelImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

@Order(ExternalSystemConstants.UNORDERED + 2) // should run after KotlinMppGradleProjectResolver
internal class ComposeResourcesProjectResolver : AbstractProjectResolverExtension() {
  override fun getExtraProjectModelClasses() =
    setOf(ComposeResourcesModel::class.java)

  override fun getToolingExtensionsClasses() =
    setOf(ComposeResourcesModelBuilder::class.java, ComposeResourcesExtension::class.java)

  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    val composeResourcesModel = resolverCtx.getExtraProject(gradleModule, ComposeResourcesModel::class.java)
    val customComposeResourcesDirs = composeResourcesModel?.customComposeResourcesDirs.orEmpty()
    log.info("Custom composeResources registered for ${gradleModule.name}: $customComposeResourcesDirs")
    val mppModel = resolverCtx.getExtraProject(gradleModule, KotlinMPPGradleModel::class.java) ?: return
    val composeResourcesDirs = gradleModule.commonComposeResourcesDirs() + mppModel.sourceSetsByName
      .keys
      .associateWith { sourceSetName ->
        val defaultComposeResourcesDir = gradleModule.defaultComposeResourcesDirFor(sourceSetName)
        val directoryPath = customComposeResourcesDirs[sourceSetName] ?: defaultComposeResourcesDir
        directoryPath
      }

    val composeResources = ComposeResourcesModelImpl(customComposeResourcesDirs = composeResourcesDirs)
    ideModule.createChild(COMPOSE_RESOURCES_KEY, composeResources)
    super.populateModuleExtraModels(gradleModule, ideModule)
  }

  /**
   * Provide common composeResources default dirs even for single target Compose projects
   * for which mppModel doesn't list common source sets
   */
  private fun IdeaModule.commonComposeResourcesDirs(): Map<String, String> =
    mapOf(
      "commonMain" to defaultComposeResourcesDirFor("commonMain"),
      "commonTest" to defaultComposeResourcesDirFor("commonTest"),
    )

  private fun IdeaModule.defaultComposeResourcesDirFor(sourceSetName: String): String = gradleProject.projectDirectory
    .resolve("src")
    .resolve(sourceSetName)
    .resolve(COMPOSE_RESOURCES_DIR)
    .absolutePath

  companion object {
    val log = Logger.getInstance(this::class.java)
  }
}