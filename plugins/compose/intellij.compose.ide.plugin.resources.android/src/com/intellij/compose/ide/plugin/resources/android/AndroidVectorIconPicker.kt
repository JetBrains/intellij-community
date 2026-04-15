// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.android

import com.android.tools.idea.npw.assetstudio.MaterialDesignIcons
import com.android.tools.idea.npw.assetstudio.ui.IconPickerDialog
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.BaseVectorIconPicker
import java.net.URL
import java.util.concurrent.CancellationException

/** Android implementation of vector icon picker using Material Design Icons */
class AndroidVectorIconPicker : BaseVectorIconPicker {
  override fun pickIcon(): BaseVectorIconPicker.Result? {
    val dialog = IconPickerDialog(null)
    val ok = try {
      dialog.showAndGet()
    }
    catch (_: CancellationException) {
      false
    }
    if (!ok) return null

    val selectedIcon = dialog.selectedIcon ?: return null
    return BaseVectorIconPicker.Result(
      name = selectedIcon.name,
      url = selectedIcon.url
    )
  }

  override fun getDefaultIconUrl(): URL = MaterialDesignIcons.getDefaultIcon()
}