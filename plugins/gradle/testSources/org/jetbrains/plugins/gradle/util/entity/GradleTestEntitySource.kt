// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.entity

import org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

data class GradleTestEntitySource(
  override val projectPath: String,
  override val phase: GradleSyncPhase,
) : GradleEntitySource
