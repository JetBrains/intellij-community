// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemView.security.backend

import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
interface ProblemsViewSubTabHost {

  val hostTabId: String

  val shownSubTabId: StateFlow<String?>

  fun selectSubTab(@NonNls subTabId: String)

  fun findSubTab(@NonNls subTabId: String): ProblemsViewSubTab?
}
