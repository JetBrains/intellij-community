// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrCellInitParams
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class LcrCellInitParamsImpl : LcrInitParamsImpl(), LcrCellInitParams {

  override var stripHorizontalInsets: Boolean = true

  override var opaque: Boolean = false
}