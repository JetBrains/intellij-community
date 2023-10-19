// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.platform.ide.progress.ComponentModalTaskOwner
import com.intellij.platform.ide.progress.GuessModalTaskOwner
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.ProjectModalTaskOwner
import java.awt.Window

internal fun ownerWindow(owner: ModalTaskOwner): Window? {
  return when (owner) {
    is ComponentModalTaskOwner -> ProgressWindow.calcParentWindow(owner.component, null)
    is ProjectModalTaskOwner -> ProgressWindow.calcParentWindow(null, owner.project)
    is GuessModalTaskOwner -> ProgressWindow.calcParentWindow(null, null) // guess
  }
}
