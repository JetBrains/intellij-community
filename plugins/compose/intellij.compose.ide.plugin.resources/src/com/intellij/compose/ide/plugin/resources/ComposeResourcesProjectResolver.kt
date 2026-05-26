// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesExtension
import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesModel
import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesModelBuilder
import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesModelImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleJava.run.usesComposeGradlePlugin
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Order(ExternalSystemConstants.UNORDERED + 2) // should run after KotlinMppGradleProjectResolver
internal class ComposeResourcesProjectResolver : AbstractProjectResolverExtension() {
  override fun getExtraProjectModelClasses() =
    setOf(ComposeResourcesModel::class.java)

  override fun getToolingExtensionsClasses() =
    setOf(ComposeResourcesModelBuilder::class.java, ComposeResourcesExtension::class.java)

  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    super.populateModuleExtraModels(gradleModule, ideModule)
    if (!usesComposeGradlePlugin(ideModule)) return
    val mppModel = resolverCtx.getExtraProject(gradleModule, KotlinMPPGradleModel::class.java)
    val kotlinGradleModel = resolverCtx.getExtraProject(gradleModule, KotlinGradleModel::class.java)
    val composeResourcesModel = resolverCtx.getExtraProject(gradleModule, ComposeResourcesModel::class.java)
    val customComposeResourcesDirs = composeResourcesModel?.customComposeResourcesDirs.orEmpty()
    log.info("Custom composeResources registered for ${gradleModule.name}: $customComposeResourcesDirs")
    if (mppModel == null) {
      log.info(
        "ComposeResources: MPP model null for ${gradleModule.name}. " +
        "Kotlin target: ${kotlinGradleModel?.kotlinTarget}; " +
        "Kotlin sourceSets: ${kotlinGradleModel?.compilerArgumentsBySourceSet?.keys}; custom keys: ${customComposeResourcesDirs.keys}"
      )
    }
    else {
      log.info("ComposeResources: Found MPP model for ${gradleModule.name}. Using sourceSets: ${mppModel.sourceSetsByName.keys}")
    }

    val composeResourcesDirs = composeResourcesDirsBySourceSetName(
      projectDirectory = gradleModule.gradleProject.projectDirectory.toPath(),
      mppSourceSetNames = mppModel?.sourceSetsByName?.keys,
      compiledKotlinSourceSetNames = kotlinGradleModel?.compiledSourceSetNames(),
      isAndroidKotlinProject = kotlinGradleModel?.isAndroidKotlinProject() == true,
      customComposeResourcesDirs = customComposeResourcesDirs,
    )

    val isPublicResClass = composeResourcesModel?.isPublicResClass ?: false
    val nameOfResClass = composeResourcesModel?.nameOfResClass ?: "Res"
    val packageOfResClass = composeResourcesModel?.packageOfResClass ?: ""
    val composeResources = ComposeResourcesModelImpl(
      customComposeResourcesDirs = composeResourcesDirs,
      isPublicResClass = isPublicResClass,
      nameOfResClass = nameOfResClass,
      packageOfResClass = packageOfResClass
    )
    ideModule.createChild(COMPOSE_RESOURCES_KEY, composeResources)
  }

  private fun KotlinGradleModel.isAndroidKotlinProject(): Boolean = kotlinTarget == KOTLIN_ANDROID

  private fun KotlinGradleModel.compiledSourceSetNames(): Set<String> {
    // KotlinGradleModelBuilder keeps a key for every source set selected for Kotlin compilation,
    // respecting Android variant requests. Compiler arguments may be empty, so the keys are the stable signal.
    // A missing key means the source set is not compiled/imported.
    return compilerArgumentsBySourceSet.keys
  }

  companion object {
    private val log = logger<ComposeResourcesProjectResolver>()

    private const val KOTLIN_ANDROID = "kotlin-android"
  }
}

internal fun composeResourcesDirsBySourceSetName(
  projectDirectory: Path,
  mppSourceSetNames: Collection<String>?,
  compiledKotlinSourceSetNames: Collection<String>?,
  isAndroidKotlinProject: Boolean,
  customComposeResourcesDirs: Map<String, Pair<String, Boolean>>,
): Map<String, Pair<String, Boolean>> {
  val defaultSourceSetNames = when {
    mppSourceSetNames != null -> commonComposeResourcesSourceSetNames + mppSourceSetNames
    isAndroidKotlinProject -> androidComposeResourcesSourceSetNames
    compiledKotlinSourceSetNames != null -> compiledKotlinSourceSetNames
    else -> commonComposeResourcesSourceSetNames
  }

  return buildMap {
    defaultSourceSetNames.forEach { put(it, projectDirectory.defaultComposeResourcesDirFor(it) to /*isCustom*/ false) }
    putAll(customComposeResourcesDirs)
  }
}

private val commonComposeResourcesSourceSetNames = setOf("commonMain", "commonTest")
private val androidComposeResourcesSourceSetNames = setOf("main")

private fun Path.defaultComposeResourcesDirFor(sourceSetName: String): String =
  resolve("src", sourceSetName, COMPOSE_RESOURCES_DIR).absolutePathString()