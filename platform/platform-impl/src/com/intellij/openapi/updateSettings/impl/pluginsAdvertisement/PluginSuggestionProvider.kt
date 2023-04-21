// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import java.util.function.Function

interface PluginSuggestionProvider {
  fun getSuggestion(project: Project, file: VirtualFile): Function<FileEditor, EditorNotificationPanel?>?
}
