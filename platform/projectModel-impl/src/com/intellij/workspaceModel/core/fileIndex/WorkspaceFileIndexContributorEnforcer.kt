// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import org.jetbrains.annotations.ApiStatus

/**
 * Used to force root registration for [WorkspaceFileIndexContributor].
 *
 * This is necessary because the flex plugin uses FlexCompositeSdk, which is not a real SDK and does not have a corresponding entity in
 * the [com.intellij.platform.diagnostic.telemetry.WorkspaceModel].
 *
 * Instead, it consists of one or more SDKs of type FlexSdkType2.
 * These however are real SDKs with corresponding [com.intellij.platform.workspace.jps.entities.SdkEntity] in the workspace model.
 *
 * But since we only contribute roots when there are references to this [com.intellij.platform.workspace.jps.entities.SdkEntity],
 * we don't have them for SDKs of type FlexSdkType2 when they are only referenced from FlexCompositeSdk.
 *
 */
@ApiStatus.Internal
@Deprecated("Internal use only")
interface WorkspaceFileIndexContributorEnforcer {
  companion object {
    val EP_NAME: ExtensionPointName<WorkspaceFileIndexContributorEnforcer> =
      ExtensionPointName("com.intellij.workspaceFileIndexContributorEnforcer")
  }
  fun shouldContribute(entity: WorkspaceEntity, storage: EntityStorage): Boolean
}