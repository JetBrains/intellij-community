// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrIconInitParams
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JLabel

@ApiStatus.Internal
internal class LcrIconImpl : LcrCellBaseImpl() {

  override val component: JLabel = JLabel()

  fun init(icon: Icon, initParams: LcrIconInitParams) {
    component.icon = icon
    component.accessibleContext.accessibleName = initParams.accessibleName
  }
}
