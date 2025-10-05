// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.plugincomponents

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFilePropertyEvent
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter
import com.intellij.util.application
import org.editorconfig.EditorConfigRegistry
import org.editorconfig.Utils

internal class EditorConfigModificationListener : BulkVirtualFileListenerAdapter(object : VirtualFileListener {
  override fun propertyChanged(event: VirtualFilePropertyEvent) {
    if ("name" == event.propertyName
        && (Utils.isEditorConfigName(event.newValue as String?)
            || Utils.isEditorConfigName(event.getOldValue() as String?))) {
      fireEditorConfigChanged(event.file)
    }
  }

  override fun fileCreated(event: VirtualFileEvent) = notifyMaybeEditorConfigFileChange(event)
  override fun fileDeleted(event: VirtualFileEvent) = notifyMaybeEditorConfigFileChange(event)
  override fun contentsChanged(event: VirtualFileEvent) = notifyMaybeEditorConfigFileChange(event)

  private fun notifyMaybeEditorConfigFileChange(event: VirtualFileEvent) {
    if (Utils.isEditorConfigFile(event.file)) {
      fireEditorConfigChanged(event.file)
    }
  }

  private fun fireEditorConfigChanged(ecFile: VirtualFile) {
    if (application.isUnitTestMode && !Utils.isEnabledInTests) {
      return
    }
    for (project in ProjectManager.getInstance().openProjects) {
      if (ProjectRootManager.getInstance(project).fileIndex.isInContent(ecFile)
          || !EditorConfigRegistry.shouldStopAtProjectRoot()) {
        Utils.fireEditorConfigChanged(project)
      }
    }
  }
}) 
