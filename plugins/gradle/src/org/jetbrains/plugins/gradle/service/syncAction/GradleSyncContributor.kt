// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor.Order.CONTENT_ROOT_CONTRIBUTOR
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor.Order.PROJECT_ROOT_CONTRIBUTOR
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor.Order.SOURCE_ROOT_CONTRIBUTOR

/**
 * The [GradleSyncContributor] is used for the IDE project configuration in the [com.intellij.platform.backend.workspace.WorkspaceModel].
 *
 * The [com.intellij.openapi.externalSystem.util.Order] annotation defines the execution order for these contributors.
 *
 * @see com.intellij.platform.backend.workspace.WorkspaceModel
 * @see com.intellij.openapi.externalSystem.util.Order
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
   * @param storage the project model state that was built based on the project model snapshot.
   *
   * @see ProjectResolverContext.getAllBuilds
   * @see ProjectResolverContext.getProjectModel
   */
  suspend fun configureProjectModel(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
  )

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<GradleSyncContributor> = ExtensionPointName.create("org.jetbrains.plugins.gradle.syncContributor")
  }

  object Order {

    /**
     * The [PROJECT_ROOT_CONTRIBUTOR] configures the IDE project root and their basic module info.
     *
     * @see org.jetbrains.plugins.gradle.service.syncContributor.GradleProjectRootSyncContributor
     */
    const val PROJECT_ROOT_CONTRIBUTOR = 0

    const val DECLARATIVE_CONTRIBUTOR = 1

    /**
     * The [CONTENT_ROOT_CONTRIBUTOR] configures the IDE project content root structure and their basic module info.
     *
     * @see org.jetbrains.plugins.gradle.service.syncContributor.GradleContentRootSyncContributor
     */
    const val CONTENT_ROOT_CONTRIBUTOR = 1000

    /**
     * The [SOURCE_ROOT_CONTRIBUTOR] configures the IDE content roots and source folder structure for each Gradle source sets.
     *
     * @see org.jetbrains.plugins.gradle.service.syncContributor.GradleSourceRootSyncContributor
     */
    const val SOURCE_ROOT_CONTRIBUTOR = 2000
  }
}
