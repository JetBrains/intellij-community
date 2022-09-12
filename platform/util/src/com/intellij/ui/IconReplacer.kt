// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ui.icons.ReplaceableIcon
import javax.swing.Icon

interface IconReplacer {
  fun replaceIcon(icon: Icon): Icon {
    if (icon is ReplaceableIcon) {
      return icon.replaceBy(this)
    }
    return icon
  }
}