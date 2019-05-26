// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface AmendCommitAware {
  fun isAmendCommitSupported(): Boolean
}