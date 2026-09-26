// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.SymbolicEntityId

class GradleProjectEntityId(
  val buildId: GradleBuildEntityId,
  val identityPath: String,
): SymbolicEntityId<GradleProjectEntity> {
  override val presentableName: @NlsSafe String
    get() = identityPath

  @Transient
  private var codeCache: Int = 0

  override fun toString(): String {
    return "GradleProjectEntityId(identityPath=$identityPath, buildId=$buildId)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GradleProjectEntityId

    if (codeCache != other.codeCache) return false
    if (identityPath != other.identityPath) return false
    if (buildId != other.buildId) return false

    return true
  }

  override fun hashCode(): Int {
    var result = codeCache
    result = 31 * result + identityPath.hashCode()
    result = 31 * result + buildId.hashCode()
    return result
  }
}