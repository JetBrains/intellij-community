// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    @Suppress("DEPRECATION")
    return createEditorDockContainer(
      fileEditorManager = fileEditorManager,
      loadingState = false,
      coroutineScope = coroutineScope.childScope(),
      isSingletonEditorInWindow = content is DockableEditor && content.isSingletonEditorInWindow,
    )
  }

  override fun loadContainerFrom(element: Element): DockContainer {
    @Suppress("DEPRECATION")
    val container = createEditorDockContainer(
      fileEditorManager = fileEditorManager,
      loadingState = true,
      coroutineScope = coroutineScope.childScope(),
      isSingletonEditorInWindow = false,
    )
    container.splitters.readExternal(element.getChild("state"))
    return container
  }
}

internal fun createEditorDockContainer(
  fileEditorManager: FileEditorManagerImpl,
  loadingState: Boolean,
  coroutineScope: CoroutineScope,
  isSingletonEditorInWindow: Boolean,
): DockableEditorTabbedContainer {
  var container: DockableEditorTabbedContainer? = null
  val splitters = object : EditorsSplitters(manager = fileEditorManager, coroutineScope = coroutineScope) {
    override val isSingletonEditorInWindow: Boolean
      get() = isSingletonEditorInWindow

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