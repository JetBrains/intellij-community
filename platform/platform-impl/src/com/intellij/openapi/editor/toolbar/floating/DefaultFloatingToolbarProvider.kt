// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DefaultFloatingToolbarProvider : AbstractFloatingToolbarProvider(ACTION_GROUP) {

  companion object {
    private const val ACTION_GROUP: String = "EditorContextBarMenu"
  }
}