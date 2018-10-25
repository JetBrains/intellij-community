// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.widget.EditorConfigNotificationServiceListener

val EditorConfigNotificationTopic = Topic("EDITORCONFIG_VFS_NOTIFICATION_TOPIC", EditorConfigNotificationServiceListener::class.java)

sealed class EditorConfigServiceResult
data class EditorConfigServiceLoaded(val list: List<EditorConfigPsiFile>) : EditorConfigServiceResult()
object EditorConfigServiceLoading : EditorConfigServiceResult()

interface EditorConfigFileHierarchyService {
  fun getParentEditorConfigFiles(virtualFile: VirtualFile): EditorConfigServiceResult

  companion object {
    fun getInstance(project: Project): EditorConfigFileHierarchyService {
      return ServiceManager.getService(project, EditorConfigFileHierarchyService::class.java)
    }
  }
}
