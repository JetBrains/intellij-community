// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrSeparator
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class LcrSeparatorImpl: LcrSeparator {

  override var text: String? = null
}