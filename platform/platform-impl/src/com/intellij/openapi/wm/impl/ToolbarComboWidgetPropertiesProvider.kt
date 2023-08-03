// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.internal.inspector.ComponentPropertiesProvider
import com.intellij.internal.inspector.PropertyBean

class ToolbarComboWidgetPropertiesProvider : ComponentPropertiesProvider<ToolbarComboWidget> {
  override fun getProperties(widget: ToolbarComboWidget): List<PropertyBean> {
    val res = mutableListOf<PropertyBean>()
    val leftIcons = widget.leftIcons
    if (leftIcons.size == 1) res.add(PropertyBean("icon", leftIcons[0], false))
    if (leftIcons.size > 1) res.add(PropertyBean("leftIcons", leftIcons, false))

    val rightIcons = widget.rightIcons
    if (rightIcons.size == 1) res.add(PropertyBean("rightIcon", rightIcons[0], false))
    if (rightIcons.size > 1) res.add(PropertyBean("rightIcons", rightIcons, false))

    return res
  }

  override fun getComponentClass(): Class<ToolbarComboWidget> = ToolbarComboWidget::class.java
}