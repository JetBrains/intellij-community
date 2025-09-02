// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface NoProjectStateHandler {
  companion object {
    val EP_NAME: ExtensionPointName<NoProjectStateHandler> = ExtensionPointName("com.intellij.noProjectStateHandler")
  }

  fun canHandle(): Boolean

  @RequiresEdt fun handle()
}
