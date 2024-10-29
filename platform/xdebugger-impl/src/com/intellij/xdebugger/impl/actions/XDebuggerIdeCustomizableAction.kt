// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.ide.IdeCustomizableActionHelper
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class XDebuggerIdeCustomizableAction : XDebuggerActionBase() {
  private val customizableActionHelper by lazy { IdeCustomizableActionHelper(this) }

  override fun update(e: AnActionEvent) {
    super.update(e)
    customizableActionHelper.update(e)
  }
}