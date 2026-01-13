// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.workspace.storage.SymbolicEntityId

class GradleExternalProjectEntityId(
  private val externalProjectId: ExternalProjectEntityId,
) : SymbolicEntityId<GradleExternalProjectEntity> {
  override val presentableName: @NlsSafe String
    get() = externalProjectId.presentableName

  @Transient
  private var codeCache: Int = 0

  override fun toString(): String {
    return "GradleExternalProjectEntityId(externalProjectId='$externalProjectId')"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GradleExternalProjectEntityId

    if (codeCache != other.codeCache) return false
    if (externalProjectId != other.externalProjectId) return false

    return true
  }

  override fun hashCode(): Int = 31 * codeCache + externalProjectId.hashCode()
}