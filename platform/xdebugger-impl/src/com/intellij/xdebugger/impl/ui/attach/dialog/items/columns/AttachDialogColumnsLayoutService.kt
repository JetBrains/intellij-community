// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.items.columns

class AttachDialogColumnsLayoutService {
  fun getColumnsLayout(): AttachDialogColumnsLayout {
    return AttachDialogDefaultColumnsLayout()
  }
}