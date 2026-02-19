// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import org.jetbrains.annotations.Nls

/**
 * As [com.intellij.openapi.actionSystem.ActionGroup] it might contain children [AnAction], but children
 * aren't displayed until user clicks on it.
 * this logic is part  [com.intellij.openapi.wm.impl.welcomeScreen.ActionGroupPanelWrapper]
 */
class CollapsedActionGroup(name: @Nls String, actions: List<AnAction>) : DefaultActionGroup(name, actions) {
  var collapsed: Boolean = true
}