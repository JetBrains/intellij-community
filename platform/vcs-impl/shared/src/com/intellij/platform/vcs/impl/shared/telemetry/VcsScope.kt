// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.telemetry

import com.intellij.platform.diagnostic.telemetry.Scope
import org.jetbrains.annotations.ApiStatus

@JvmField
@ApiStatus.Internal
val VcsScope: Scope = Scope("vcs")
