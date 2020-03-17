// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

class DefaultFloatingToolbarProvider : AbstractFloatingToolbarProvider(ACTION_GROUP) {

  override val priority = 0

  override val autoHideable = true

  companion object {
    const val ACTION_GROUP = "EditorContextBarMenu"
  }
}