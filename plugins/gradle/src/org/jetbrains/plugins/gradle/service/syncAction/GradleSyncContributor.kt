// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

/**
 * The [GradleSyncContributor] is used for the IDE project configuration in the [com.intellij.platform.backend.workspace.WorkspaceModel].
 */
@ApiStatus.Experimental
interface GradleSyncContributor {

  val name: String
    get() = javaClass.simpleName

  val phase: GradleSyncPhase

  /**
   * Configures a project entities based on the Gradle project models.
   *
   * Note: It is guaranteed that all phases will be handled in the strict order.
   *
   * @param context the unified container for sync settings, parameters, models, etc.
   * @param storage the entity storage that was built during the current sync.
   *
   * @return the updated entity storage.
   * All entities in the result storage should be added with entity source that implements [GradleEntitySource].
   * Entities with other source will be ignored during replacing project storage's entities by sync storage's entities.
   *
   * @see ProjectResolverContext.getAllBuilds
   * @see ProjectResolverContext.getProjectModel
   */
  suspend fun createProjectModel(
    context: ProjectResolverContext,
    storage: ImmutableEntityStorage,
  ): ImmutableEntityStorage

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<GradleSyncContributor> = ExtensionPointName.create("org.jetbrains.plugins.gradle.syncContributor")
  }
}
