// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ui.customization.BeforeAction
import com.intellij.ide.ui.customization.GroupStart
import com.intellij.ide.ui.customization.ToolbarAddQuickActionsAction

private val leftToolbarStart = GroupStart("MainToolbarNewUI", "MainToolbarLeft")
private val beforeRunWidget = BeforeAction("MainToolbarNewUI", "NewUiRunWidget").orElse(GroupStart("MainToolbarNewUI", "MainToolbarRight"))

class OpenFileQuickAction: ToolbarAddQuickActionsAction(listOf("OpenFile"), "MainToolbarNewUI", leftToolbarStart) {}
class SaveAllQuickAction: ToolbarAddQuickActionsAction(listOf("SaveAll"), "MainToolbarNewUI", leftToolbarStart) {}
class SynchronizeQuickAction: ToolbarAddQuickActionsAction(listOf("Synchronize"), "MainToolbarNewUI", leftToolbarStart) {}
class BackForwardQuickAction: ToolbarAddQuickActionsAction(listOf("Back", "Forward"), "MainToolbarNewUI", leftToolbarStart) {}
class UndoRedoQuickAction: ToolbarAddQuickActionsAction(listOf("\$Undo", "\$Redo"), "MainToolbarNewUI", leftToolbarStart) {}

class BuildQuickAction: ToolbarAddQuickActionsAction(listOf("CompileDirty"), "MainToolbarNewUI", beforeRunWidget) {}
class CoverageQuickAction: ToolbarAddQuickActionsAction(listOf("Coverage"), "MainToolbarNewUI", beforeRunWidget) {}
class ProfilerQuickAction: ToolbarAddQuickActionsAction(listOf("Profiler"), "MainToolbarNewUI", beforeRunWidget) {}
