// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.customization.GroupEnd
import com.intellij.ide.ui.customization.GroupStart
import com.intellij.ide.ui.customization.ToolbarAddQuickActionInfo
import com.intellij.idea.ActionsBundle

private val generalActionsStrategy = GroupEnd("MainToolbarNewUI", "MainToolbarGeneralActionsGroup")
private val runActionsStrategy = GroupStart("MainToolbarNewUI", "MainToolbarRight")

class SaveAllQuickAction: ToolbarAddQuickActionInfo(listOf("SaveAll"), ActionsBundle.message("MainToolbarQuickActions.SaveAll.text"), AllIcons.Actions.MenuSaveall, generalActionsStrategy)
class BackForwardQuickAction: ToolbarAddQuickActionInfo(listOf("Back", "Forward"), ActionsBundle.message("MainToolbarQuickActions.BackForward.text"), AllIcons.Actions.Back, generalActionsStrategy)

class BuildQuickAction: ToolbarAddQuickActionInfo(listOf("CompileDirty"), ActionsBundle.message("MainToolbarQuickActions.Build.text"), AllIcons.Actions.Compile, runActionsStrategy)
