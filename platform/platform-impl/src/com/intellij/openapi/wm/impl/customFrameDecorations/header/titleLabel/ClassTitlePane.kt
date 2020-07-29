// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.displayUrlRelativeToProject
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.openapi.wm.impl.FrameTitleBuilder
import com.intellij.openapi.wm.impl.PlatformFrameTitleBuilder
import java.awt.Component

class ClassTitlePane : ClippingTitle() {
  var project: Project? = null

  fun updatePath(c: Component) {
    longText = project?.let {
      val fileEditorManager = FileEditorManager.getInstance(it)

      val file = if (fileEditorManager is FileEditorManagerEx) {
        val splittersFor = fileEditorManager.getSplittersFor(c)
        splittersFor.currentFile
      }
      else {
        fileEditorManager?.selectedEditor?.file
      }

      file?.let { fl ->
        val instance = FrameTitleBuilder.getInstance()
        if (instance is PlatformFrameTitleBuilder) {
          val fileTitle = VfsPresentationUtil.getPresentableNameForUI(project!!, file)
          if (!fileTitle.endsWith(file.presentableName) || file.parent == null) {
            fileTitle
          }
          else {
            displayUrlRelativeToProject(file, file.presentableUrl, it, true, false)
          }
        }
        else {
          instance.getFileTitle(it, fl)
        }
      } ?: ""
    } ?: ""
  }
}