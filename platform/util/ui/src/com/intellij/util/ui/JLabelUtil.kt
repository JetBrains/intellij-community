// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import org.jetbrains.annotations.ApiStatus
import javax.swing.JLabel

@ApiStatus.Experimental
object JLabelUtil {
  /**
   * Presence of this key indicates that a label text can be trimmed to fit the content
   * Similar to CSS overflow property
   *
   * @see com.intellij.util.ui.SwingTextTrimmer
   */
  @JvmField
  @ApiStatus.Internal
  val TRIM_OVERFLOW_KEY: Key<Any> = Key.create("JLabel.trimOverflow")

  @JvmStatic
  fun setTrimOverflow(label: JLabel, trim: Boolean) {
    if (trim) {
      ClientProperty.put(label, TRIM_OVERFLOW_KEY, Any())
    }
    else {
      ClientProperty.put(label, TRIM_OVERFLOW_KEY, null)
    }
  }
}
