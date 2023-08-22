// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrTextInitParams
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class LcrTextInitParamsImpl: LcrInitParamsImpl(), LcrTextInitParams {

  override var style: LcrTextInitParams.Style = LcrTextInitParams.Style.NORMAL
}