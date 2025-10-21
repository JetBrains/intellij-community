// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

/**
 * Should be implemented to make [com.intellij.ide.actions.ToolWindowSplitRightAction] and [com.intellij.ide.actions.ToolWindowSplitDownAction]
 * work in your tool window.
 * These actions require the copy of the selected tool window content to split the current window with it.
 */
@ApiStatus.Experimental
interface ToolWindowSplitContentProvider {
  @RequiresEdt
  fun createContentCopy(project: Project, content: Content): Content
}

internal class ToolWindowSplitContentProviderBean : BaseKeyedLazyInstance<ToolWindowSplitContentProvider>() {
  @Attribute("toolWindowId")
  @JvmField
  @RequiredElement
  var toolWindowId: String? = null

  @Attribute("implementationClass")
  @JvmField
  @RequiredElement
  var implementationClass: String? = null

  override fun getImplementationClassName(): String? = implementationClass

  companion object {
    private val EP_NAME: ExtensionPointName<ToolWindowSplitContentProviderBean> = ExtensionPointName("com.intellij.toolWindow.splitContentProvider")

    fun getForToolWindow(toolWindowId: String): ToolWindowSplitContentProvider? {
      return EP_NAME.findFirstSafe { toolWindowId.equals(it.toolWindowId, ignoreCase = true) }?.instance
    }
  }
}