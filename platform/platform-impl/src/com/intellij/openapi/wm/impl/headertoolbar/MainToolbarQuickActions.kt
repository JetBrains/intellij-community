// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ui.customization.GroupEnd
import com.intellij.ide.ui.customization.GroupStart
import com.intellij.ide.ui.customization.ToolbarAddQuickActionsAction

private val generalActionsStrategy = GroupEnd("MainToolbarNewUI", "MainToolbarLeft")
private val runActionsStrategy = GroupStart("MainToolbarNewUI", "MainToolbarRight")

class OpenFileQuickAction: ToolbarAddQuickActionsAction(listOf("OpenFile"), "MainToolbarNewUI", generalActionsStrategy) {}
class SaveAllQuickAction: ToolbarAddQuickActionsAction(listOf("SaveAll"), "MainToolbarNewUI", generalActionsStrategy) {}
class SynchronizeQuickAction: ToolbarAddQuickActionsAction(listOf("Synchronize"), "MainToolbarNewUI", generalActionsStrategy) {}
class BackForwardQuickAction: ToolbarAddQuickActionsAction(listOf("Back", "Forward"), "MainToolbarNewUI", generalActionsStrategy) {}
class UndoRedoQuickAction: ToolbarAddQuickActionsAction(listOf("\$Undo", "\$Redo"), "MainToolbarNewUI", generalActionsStrategy) {}

class BuildQuickAction: ToolbarAddQuickActionsAction(listOf("CompileDirty"), "MainToolbarNewUI", runActionsStrategy) {}
class CoverageQuickAction: ToolbarAddQuickActionsAction(listOf("Coverage"), "MainToolbarNewUI", runActionsStrategy) {}
class ProfilerQuickAction: ToolbarAddQuickActionsAction(listOf("Profiler"), "MainToolbarNewUI", runActionsStrategy) {}
