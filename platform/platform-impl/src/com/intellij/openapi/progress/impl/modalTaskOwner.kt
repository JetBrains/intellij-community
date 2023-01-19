// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import java.awt.Component
import java.awt.Window

internal class ComponentModalTaskOwner(val component: Component) : ModalTaskOwner

internal class ProjectModalTaskOwner(val project: Project) : ModalTaskOwner

internal fun ownerWindow(owner: ModalTaskOwner): Window? {
  return when (owner) {
    is ComponentModalTaskOwner -> ProgressWindow.calcParentWindow(owner.component, null)
    is ProjectModalTaskOwner -> ProgressWindow.calcParentWindow(null, owner.project)
    else -> ProgressWindow.calcParentWindow(null, null) // guess
  }
}
