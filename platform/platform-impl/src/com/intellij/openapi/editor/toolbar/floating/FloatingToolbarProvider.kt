// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.actionSystem.ActionGroup

interface FloatingToolbarProvider {

  val id: String

  val autoHideable: Boolean

  val actionGroup: ActionGroup
}