// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PerformGCAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    System.gc()
  }
}