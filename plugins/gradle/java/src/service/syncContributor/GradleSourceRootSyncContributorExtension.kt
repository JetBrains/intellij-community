// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

@ApiStatus.NonExtendable
interface GradleSourceSetSyncContext {
  val resolverContext: ProjectResolverContext
  val buildModel: GradleLightBuild
  val projectModel: GradleLightProject
  val moduleEntity: ModuleEntityBuilder
  val sourceSetName: String
}

interface GradleSourceRootSyncContributorExtension {
  suspend fun configureSourceSetModules(context: GradleSourceSetSyncContext)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<GradleSourceRootSyncContributorExtension> = ExtensionPointName.create("org.jetbrains.plugins.gradle.sourceRootSyncContributor")
  }
}