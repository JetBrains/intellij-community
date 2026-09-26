// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TransparentComponent {

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun isComponentOnHold(): Boolean

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun setOpacity(opacity: Float)

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun showComponent()

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun hideComponent()

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun repaintComponent()
}