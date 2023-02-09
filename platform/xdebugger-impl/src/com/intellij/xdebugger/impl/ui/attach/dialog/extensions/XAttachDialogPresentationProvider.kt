// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogHostType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
interface XAttachDialogPresentationProvider {

  companion object {
    val EP: ExtensionPointName<XAttachDialogPresentationProvider> = ExtensionPointName.create(
      "com.intellij.xdebugger.dialog.presentation.provider")
  }

  @Nls
  @NlsContexts.Button
  fun getCustomHostTypeDisplayText(hostType: AttachDialogHostType): String?

  fun getPriority(): Int
}