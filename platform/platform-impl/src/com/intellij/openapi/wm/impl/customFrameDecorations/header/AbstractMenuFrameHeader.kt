// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import java.awt.Color
import javax.swing.JFrame

internal abstract class AbstractMenuFrameHeader(frame: JFrame) : FrameHeader(frame) {
  abstract fun updateMenuActions(forceRebuild: Boolean)
}