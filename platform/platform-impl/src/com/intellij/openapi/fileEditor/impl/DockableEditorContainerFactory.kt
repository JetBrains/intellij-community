// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockContainerFactory
import com.intellij.ui.docking.DockableContent
import kotlinx.coroutines.CoroutineScope
import org.jdom.Element
import org.jetbrains.annotations.NonNls

internal class DockableEditorContainerFactory(private val fileEditorManager: FileEditorManagerImpl,
                                              private val coroutineScope: CoroutineScope) : DockContainerFactory.Persistent {
  companion object {
    const val TYPE: @NonNls String = "file-editors"
  }

  override fun createContainer(content: DockableContent<*>?): DockContainer {
    return createContainer(loadingState = false, coroutineScope = coroutineScope.childScope())
  }

  private fun createContainer(loadingState: Boolean, coroutineScope: CoroutineScope): DockableEditorTabbedContainer {
    var container: DockableEditorTabbedContainer? = null
    val splitters = object : EditorsSplitters(manager = fileEditorManager, coroutineScope = coroutineScope) {
      override fun afterFileClosed(file: VirtualFile) {
        container!!.fireContentClosed(file)
      }

      override fun afterFileOpen(file: VirtualFile) {
        container!!.fireContentOpen(file)
      }

      override val isFloating: Boolean
        get() = true
    }
    if (!loadingState) {
      splitters.createCurrentWindow()
    }
    container = DockableEditorTabbedContainer(splitters = splitters, disposeWhenEmpty = true, coroutineScope = coroutineScope)
    return container
  }

  override fun loadContainerFrom(element: Element): DockContainer {
    val container = createContainer(loadingState = true, coroutineScope = coroutineScope.childScope())
    container.splitters.readExternal(element.getChild("state"))
    return container
  }
}