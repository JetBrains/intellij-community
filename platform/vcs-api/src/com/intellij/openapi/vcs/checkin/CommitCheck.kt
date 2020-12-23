// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Sentence

@ApiStatus.Experimental
interface CommitCheck<T : CommitProblem> {
  @RequiresEdt
  fun isEnabled(): Boolean

  @RequiresEdt
  suspend fun runCheck(): T?

  @RequiresEdt
  fun showDetails(problem: T)
}

@ApiStatus.Experimental
interface CommitProblem {
  @get:Nls(capitalization = Sentence)
  val text: String
}