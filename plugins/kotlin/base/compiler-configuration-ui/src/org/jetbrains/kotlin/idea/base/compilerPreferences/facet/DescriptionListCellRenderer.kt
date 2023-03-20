// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.compilerPreferences.facet

import org.jetbrains.kotlin.utils.DescriptionAware
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class DescriptionListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
        text = (value as? DescriptionAware)?.description ?: ""
    }
}