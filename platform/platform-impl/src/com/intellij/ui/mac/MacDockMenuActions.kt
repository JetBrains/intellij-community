// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac

import org.jetbrains.annotations.ApiStatus
import java.awt.MenuItem

/**
 * Implement this interface and register the implementation as `com.intellij.mac.dockMenuActions` extension to add an action to the dock 
 * menu on macOS.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface MacDockMenuActions {
  /**
   * This method is called once, and if it returns a non-null instance, the corresponding item is added to the dock menu on macOS.
   */
  fun createMenuItem(): MenuItem?
}