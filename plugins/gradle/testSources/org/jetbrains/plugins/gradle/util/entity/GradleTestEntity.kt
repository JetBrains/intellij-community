// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.entity

import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

interface GradleTestEntity : WorkspaceEntityWithSymbolicId {

  val phase: GradleSyncPhase

  override val symbolicId: GradleTestEntityId
    get() = GradleTestEntityId(phase)
}
