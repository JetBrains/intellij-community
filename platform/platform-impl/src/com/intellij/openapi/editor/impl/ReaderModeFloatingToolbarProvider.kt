// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider

class ReaderModeFloatingToolbarProvider : AbstractFloatingToolbarProvider("ReaderModeActionGroup") {
  override val priority = 200
  override val autoHideable = true
}