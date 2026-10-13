// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus

/**
 * Converts the [Content] of a tool window tab hosted in an editor tab to and from XML, so that the platform can carry
 * it in the editor state of the tab and restore the tab after a restart.
 *
 * Register an implementation using the `com.intellij.toolWindowEditorTabPersistenceProvider`
 * extension point. The extension is keyed by tool window ID: the extension `key`
 * must match the ID of the tool window.
 * A tool window without a [ToolWindowEditorTabPersistenceProvider] can still move its tabs
 * into the editor. Those tabs are not restored.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface ToolWindowEditorTabPersistenceProvider {

  /**
   * Whether [serialize] can produce state this provider is able to restore [content] from.
   */
  @RequiresEdt
  fun canSerialize(content: Content): Boolean

  /**
   * Serializes [content] into an [Element].
   *
   * Contract: Only called for a [content] that [canSerialize] accepted.
   */
  @RequiresEdt
  fun serialize(content: Content): Element

  /**
   * Recreates the content described by [element], or returns `null` when it cannot be restored.
   */
  @RequiresEdt
  fun deserialize(project: Project, element: Element): Content?
}
