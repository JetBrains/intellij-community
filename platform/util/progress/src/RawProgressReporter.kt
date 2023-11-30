// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable

@Experimental
@NonExtendable
interface RawProgressReporter {

  /**
   * Updates the current progress text.
   */
  fun text(text: @ProgressText String?): Unit = Unit

  /**
   * Updates the current progress details.
   */
  fun details(details: @ProgressDetails String?): Unit = Unit

  /**
   * Updates the current progress fraction.
   *
   * @param fraction a number between 0.0 and 1.0 reflecting the ratio of work that has already been done (0.0 for nothing, 1.0 for all),
   * or `null` to clear the fraction and make the progress indeterminate
   */
  fun fraction(fraction: Double?): Unit = Unit
}
