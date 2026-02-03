// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.vcs.log.VcsLogBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
object VcsActivityKey : ActivityKey {
  override val presentableName: @Nls String
    get() = VcsLogBundle.message("activity.tracker.vcs.log")
}
