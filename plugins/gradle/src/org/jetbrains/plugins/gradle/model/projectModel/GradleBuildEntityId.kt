// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

class GradleBuildEntityId(
  private val externalProjectId: ExternalProjectEntityId,
  private val url: VirtualFileUrl,
) : SymbolicEntityId<GradleBuildEntity> {

  override val presentableName: @NlsSafe String
    get() = url.fileName

  @Transient
  private var codeCache: Int = 0

  override fun toString(): String {
    return "GradleBuildEntityId(externalProjectId=$externalProjectId, url=$url)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GradleBuildEntityId

    if (codeCache != other.codeCache) return false
    if (externalProjectId != other.externalProjectId) return false
    if (url != other.url) return false

    return true
  }

  override fun hashCode(): Int {
    var result = codeCache
    result = 31 * result + externalProjectId.hashCode()
    result = 31 * result + url.hashCode()
    return result
  }
}