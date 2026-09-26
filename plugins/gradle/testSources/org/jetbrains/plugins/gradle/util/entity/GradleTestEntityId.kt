// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.entity

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.SymbolicEntityId
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

data class GradleTestEntityId(
  val phase: GradleSyncPhase,
) : SymbolicEntityId<GradleTestEntity> {

  override val presentableName: @NlsSafe String
    get() = "GradleTestEntity for $phase"

  override fun toString(): String = phase.name
}
