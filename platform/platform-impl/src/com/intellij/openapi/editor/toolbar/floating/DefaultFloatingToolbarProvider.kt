// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DefaultFloatingToolbarProvider : AbstractFloatingToolbarProvider(ACTION_GROUP) {

  companion object {
    @Language("devkit-action-id")
    private const val ACTION_GROUP: String = "EditorContextBarMenu"
  }
}