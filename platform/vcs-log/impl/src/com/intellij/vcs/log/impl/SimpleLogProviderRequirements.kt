// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.vcs.log.VcsLogProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class SimpleLogProviderRequirements(override val commitCount: Int) : VcsLogProvider.Requirements
