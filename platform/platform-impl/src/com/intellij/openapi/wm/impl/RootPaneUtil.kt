// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.platform.ide.menu.IdeJMenuBar
import kotlinx.coroutines.CoroutineScope
import javax.swing.JFrame

internal object RootPaneUtil {
  fun createMenuBar(coroutineScope: CoroutineScope, frame: JFrame, customMenuGroup: ActionGroup?): IdeJMenuBar {
    return IdeJMenuBar(coroutineScope = coroutineScope, frame = frame, customMenuGroup = customMenuGroup)
  }
}