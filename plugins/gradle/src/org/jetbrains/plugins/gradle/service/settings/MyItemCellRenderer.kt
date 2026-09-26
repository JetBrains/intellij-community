// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import javax.swing.ListCellRenderer

internal fun <T> createItemCellRenderer(): ListCellRenderer<IdeaGradleProjectSettingsControlBuilder.MyItem<T>?> {
  return listCellRenderer("") {
    text(value.getText())

    value.getComment()?.let {
      text(it) {
        foreground = greyForeground
      }
    }
  }
}