// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Extension point provides a way to add a new column to VCS Log (e.g. Build Status, Attached Reviews, Commit Verification Status)
 *
 * @see VcsLogColumn for more details about column customization
 */
interface VcsLogCustomColumn<T> : VcsLogColumn<T> {

  /**
   * @return [true] if column should be visible by default. [false] if column should be hidden.
   *
   * It is possible to show/hide column under "Eye" icon -> Show Columns -> [localizedName]
   */
  @JvmDefault
  fun isEnabledByDefault() = true

  companion object {
    @JvmField
    val KEY = ExtensionPointName<VcsLogCustomColumn<*>>("com.intellij.vcsLogCustomColumn")
  }
}