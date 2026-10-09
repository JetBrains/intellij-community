// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RealClickDetector {
  // On Windows, to work around JBR-10042, we always treat "mouse released" events as true clicks.
  // It's not an issue, because this hack is only needed for OS where a right click
  // can both open a menu and activate an item, which means macOS and Linux.
  private var isRealClick = SystemInfoRt.isWindows

  fun mousePressed() {
    if (!isRealClick) {
      LOG.debug("A MOUSE_PRESSED event is detected, treating future MOUSE_RELEASED events as real ones")
    }
    isRealClick = true
  }

  fun isRealClick(): Boolean {
    if (!isRealClick) {
      // Sometimes happens on Wayland. Popups and menus may receive the released event from the same mouse press that invoked the context menu or the popup.
      // This leads to an immediate click on the menu / popup item that happens to be under the cursor.
      // Normally there isn't one, but if the menu / popup had to be repositioned because it's close to a screen edge, it can happen (IJPL-253484).
      // We don't check for Wayland here because handling a MOUSE_RELEASED without a MOUSE_PRESSED one doesn't make sense in any case.
      // The only exception is a single press-drag-release, but that's specific to menus and handled separately there
      // (com.intellij.ui.plaf.beg.BegMenuItemUI.MyMenuDragMouseHandler).
      LOG.debug("Ignoring a MOUSE_RELEASED event because there was no MOUSE_PRESSED")
    }
    return isRealClick
  }
}

private val LOG = logger<RealClickDetector>()
