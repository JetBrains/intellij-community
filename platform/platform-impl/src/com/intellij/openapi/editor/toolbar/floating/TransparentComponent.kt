// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TransparentComponent {

  val autoHideable: Boolean

  @RequiresEdt
  fun setOpacity(opacity: Float)

  @RequiresEdt
  fun showComponent()

  @RequiresEdt
  fun hideComponent()

  @RequiresEdt
  fun isComponentUnderMouse(): Boolean

  @RequiresEdt
  fun repaintComponent()
}