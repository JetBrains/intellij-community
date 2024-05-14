// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons

import javax.swing.JComponent

internal sealed interface CustomFrameButtons {

  var isCompactMode: Boolean

  fun getContent(): JComponent

  fun updateVisibility()

  fun onUpdateFrameActive()
}