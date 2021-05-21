// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.openapi.extensions.ExtensionPointName

interface VcsLogCustomColumn<T> : VcsLogColumn<T> {

  @JvmDefault
  fun isEnabledByDefault() = true

  companion object {
    @JvmField
    val KEY = ExtensionPointName<VcsLogCustomColumn<*>>("com.intellij.vcsLogCustomColumn")
  }
}