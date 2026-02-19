// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.telemetry

import com.intellij.platform.vcs.impl.shared.telemetry.VcsTelemetrySpan
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GitTelemetrySpan : VcsTelemetrySpan

internal enum class GitBranchesPopupSpan : GitTelemetrySpan {
  BuildingTree {
    override fun getName() = "git-branches-popup-building-tree"
  }
}
