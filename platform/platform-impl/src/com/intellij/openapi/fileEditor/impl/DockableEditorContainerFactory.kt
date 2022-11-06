// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ProjectFrameHelper.Companion.getFrameHelper
import com.intellij.ui.ComponentUtil
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockContainerFactory
import com.intellij.ui.docking.DockableContent
import org.jdom.Element
import org.jetbrains.annotations.NonNls

internal class DockableEditorContainerFactory(private val fileEditorManager: FileEditorManagerImpl) : DockContainerFactory.Persistent {
  companion object {
    const val TYPE: @NonNls String = "file-editors"
  }

  override fun createContainer(content: DockableContent<*>?): DockContainer = createContainer(false)

  private fun createContainer(loadingState: Boolean): DockableEditorTabbedContainer {
    var container: DockableEditorTabbedContainer? = null
    val splitters = object : EditorsSplitters(fileEditorManager) {
      override fun afterFileClosed(file: VirtualFile) {
        container!!.fireContentClosed(file)
      }

      override fun afterFileOpen(file: VirtualFile) {
        container!!.fireContentOpen(file)
      }

      override fun getFrame(project: Project): IdeFrameEx? {
        val frame = ComponentUtil.findUltimateParent(this)
        return if (frame is IdeFrameEx) frame else getFrameHelper(frame as IdeFrameImpl)
      }

      override val isFloating: Boolean
        get() = true
    }
    if (!loadingState) {
      splitters.createCurrentWindow()
    }
    container = DockableEditorTabbedContainer(splitters, true)
    return container
  }

  override fun loadContainerFrom(element: Element): DockContainer {
    val container = createContainer(true)
    container.splitters.readExternal(element.getChild("state"))
    return container
  }
}