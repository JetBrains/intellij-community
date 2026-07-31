// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.diff

import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface DiffRequestChainChangeSetProvider {
  suspend fun loadChanges(): List<Change>
}
