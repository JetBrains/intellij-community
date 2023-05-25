// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface LcrTextInitParams : LcrInitParams {

  enum class Style {
    NORMAL,

    /**
     * A gray text, that is usually used for non-primary information in renderers
     */
    GRAYED
  }

  var style: Style
}