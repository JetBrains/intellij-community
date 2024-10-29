// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrRow
import com.intellij.ui.dsl.listCellRenderer.UiDslRendererProvider
import org.jetbrains.annotations.ApiStatus
import javax.swing.ListCellRenderer

@ApiStatus.Internal
open class DefaultUiDslRendererProvider : UiDslRendererProvider {

  override fun <T> getLcrRenderer(renderContent: LcrRow<T>.() -> Unit): ListCellRenderer<T> {
    return LcrRowImpl(renderContent)
  }
}
