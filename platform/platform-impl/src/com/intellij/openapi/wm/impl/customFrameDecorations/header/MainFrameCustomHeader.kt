// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import javax.swing.JComponent

interface MainFrameCustomHeader {
  suspend fun updateMenuActions(forceRebuild: Boolean) {
  }

  fun getComponent(): JComponent
}