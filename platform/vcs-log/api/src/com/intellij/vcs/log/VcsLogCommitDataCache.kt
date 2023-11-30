// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface VcsLogCommitDataCache<T : VcsShortCommitDetails> {
  fun getCachedData(commit: Int): T?
}