// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrRow
import com.intellij.ui.dsl.listCellRenderer.UiDslRendererProvider
import javax.swing.ListCellRenderer

class DefaultDslRendererProvider : UiDslRendererProvider {
  override fun isApplicable(): Boolean {
    return true
  }

  override fun <T> getRenderer(renderContent: LcrRow<T>.() -> Unit): ListCellRenderer<T> {
    return LcrRowImpl(renderContent)
  }
}