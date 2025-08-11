// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.entity

import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource

data class GradleTestBridgeEntitySource(
  override val projectPath: String,
  override val phase: GradleSyncPhase,
) : GradleBridgeEntitySource
