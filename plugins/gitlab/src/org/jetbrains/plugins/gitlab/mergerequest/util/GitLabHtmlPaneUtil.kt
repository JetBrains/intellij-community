// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import com.intellij.collaboration.ui.onHyperlinkActivated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.BrowserHyperlinkListener
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import java.nio.file.Path
import javax.swing.JEditorPane

fun JEditorPane.addGitLabHyperlinkListener(project: Project?) {
  onHyperlinkActivated { e ->
    if (e.description.startsWith(GitLabUIUtil.OPEN_FILE_LINK_PREFIX) && project != null) {
      val path = e.description.removePrefix(GitLabUIUtil.OPEN_FILE_LINK_PREFIX)
      val file = VirtualFileManager.getInstance().findFileByNioPath(Path.of(path)) ?: return@onHyperlinkActivated
      if (!file.exists()) return@onHyperlinkActivated
      FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file), true)
      return@onHyperlinkActivated
    }

    BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(e)
  }
}
