// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.event.MouseEvent

internal class StickyLineMouseEvent(e: MouseEvent, source: Component, y: Int)
  : MouseEvent(
      source,
      e.id,
      e.`when`,
      UIUtil.getAllModifiers(e),
      e.x,
      y,
      e.clickCount,
      e.isPopupTrigger,
      e.button,
  )
