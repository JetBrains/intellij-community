// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job

/**
 * Manages child status bars for detached windows.
 * Handles creation, widget propagation, and lifecycle of child status bars.
 */
internal class ChildStatusBarManager(
  private val parent: IdeStatusBarImpl,
  private val widgetRegistry: WidgetRegistry,
) {
  @Volatile
  private var children = persistentHashSetOf<IdeStatusBarImpl>()

  val size: Int
    get() = children.size

  fun isEmpty(): Boolean = children.isEmpty()

  @RequiresEdt
  fun createChild(coroutineScope: CoroutineScope, currentFileEditorFlow: StateFlow<FileEditor?>): IdeStatusBarImpl {
    EDT.assertIsEdt()
    val bar = IdeStatusBarImpl(
      parentCs = coroutineScope,
      getProject = parent::project,
      addToolWindowWidget = false,
      currentFileEditorFlow = currentFileEditorFlow,
    )
    bar.isVisible = parent.isVisible

    synchronized(parent) {
      children = children.add(bar)
    }
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      synchronized(parent) {
        children = children.remove(bar)
      }
    }

    // Propagate ALL existing widgets to the new child
    for (bean in widgetRegistry.getAllBeans()) {
      propagateWidgetToChild(widget = bean.widget, position = bean.position, anchor = bean.order, child = bar)
    }

    return bar
  }

  fun propagateWidgetToChild(
    widget: StatusBarWidget,
    position: Position,
    anchor: LoadingOrder,
    child: IdeStatusBarImpl,
  ) {
    val childWidget: StatusBarWidget = when (widget) {
      is ChildStatusBarWidget -> widget.createForChild(child)
      is StatusBarWidget.Multiframe -> widget.copy()
      else -> return
    }

    val component = wrap(childWidget)
    if (component is StatusBarWidgetWrapper) {
      component.beforeUpdate()
    }
    child.addWidgetToSelf(WidgetBean(childWidget, position, component, anchor))
  }

  fun propagateToAll(widget: StatusBarWidget, position: Position, anchor: LoadingOrder) {
    for (child in children) {
      propagateWidgetToChild(widget, position, anchor, child)
    }
  }

  fun updateAll(action: (IdeStatusBarImpl) -> Unit) {
    for (child in children) {
      action(child)
    }
  }

  fun setVisibilityForAll(visible: Boolean) {
    for (child in children) {
      child.isVisible = visible
    }
  }
}
