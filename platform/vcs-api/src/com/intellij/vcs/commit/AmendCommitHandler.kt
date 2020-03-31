// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Experimental
interface AmendCommitHandler {
  var isAmendCommitMode: Boolean
  var isAmendCommitModeTogglingEnabled: Boolean
  fun isAmendCommitModeSupported(): Boolean
  fun addAmendCommitModeListener(listener: AmendCommitModeListener, parent: Disposable)
}

interface AmendCommitModeListener : EventListener {
  fun amendCommitModeToggled()
}