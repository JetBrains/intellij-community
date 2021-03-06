// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview.avatar

import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.Icon

/**
 * @param T - avatar key type
 */
interface AvatarIconsProvider<T> {

  /**
   * @param key - avatar key
   * @param iconSize - required icon size in pixels (unscaled)
   * @return the icon which can be used to draw the avatar
   */
  @RequiresEdt
  fun getIcon(key: T?, iconSize: Int): Icon
}
