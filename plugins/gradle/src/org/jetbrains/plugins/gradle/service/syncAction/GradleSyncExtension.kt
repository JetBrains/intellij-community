// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

/**
 * The useful Gradle sync extensions.
 *
 * These extensions cannot be added into the main [GradleSyncContributor] API.
 * However, they are needed to support backward compatibility or patch IntelliJ platform issues.
 *
 * The [com.intellij.openapi.externalSystem.util.Order] annotation defines the extensions' execution order.
 */
@ApiStatus.Experimental
interface GradleSyncExtension {

  /**
   * Updates [syncStorage] based on information from [projectStorage] and vice versa.
   *
   * Useful, when
   *  * [syncStorage] has conflicting information with current [projectStorage].
   *  * [syncStorage] should be enriched by the [projectStorage] information (e.g. from later sync phases).
   *  * [syncStorage] has non Gradle entities should be added into [projectStorage] (e.g. don't extend [GradleEntitySource]).
   *  * [projectStorage] should be enriched by the [syncStorage] information (e.g. cross build tool dependency substitution)
   *
   * Note: This function is executed inside [com.intellij.platform.backend.workspace.WorkspaceModel.update] function.
   * Here heavy-weight executions are forbidden, because it is executed under the write action.
   *
   * @param context the unified container for sync settings, parameters, models, etc.
   * @param syncStorage the sync project model builder that was built during the current sync.
   * @param projectStorage the current project model snapshot.
   */
  fun updateProjectModel(
    context: ProjectResolverContext,
    syncStorage: MutableEntityStorage,
    projectStorage: MutableEntityStorage,
    phase: GradleSyncPhase,
  ): Unit = Unit

  /**
   * Updates project model after the workspace model commit.
   *
   * Useful, when
   *  * JPS entity bridges should be updated:
   *   [com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge],
   *   [com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge]
   *
   * @param context the unified container for sync settings, parameters, models, etc.
   */
  suspend fun updateBridgeModel(
    context: ProjectResolverContext,
    phase: GradleSyncPhase,
  ): Unit = Unit

  companion object {

    val EP_NAME: ExtensionPointName<GradleSyncExtension> = ExtensionPointName.create("org.jetbrains.plugins.gradle.syncExtension")
  }
}