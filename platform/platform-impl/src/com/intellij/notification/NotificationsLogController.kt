// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification

import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface NotificationsLogController {
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun show()

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun activate(focus: Boolean = true)

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun toggle()
}