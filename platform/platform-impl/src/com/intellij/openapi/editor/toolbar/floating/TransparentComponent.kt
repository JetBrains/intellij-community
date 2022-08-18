// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import org.jetbrains.annotations.ApiStatus
import java.awt.Component


@ApiStatus.Internal
interface TransparentComponent {

  val component: Component

  val autoHideable: Boolean

  var opacity: Float

  fun showComponent()

  fun hideComponent()
}