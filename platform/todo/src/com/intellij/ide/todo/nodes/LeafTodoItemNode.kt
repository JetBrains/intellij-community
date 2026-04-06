// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.nodes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.NotNull

/**
 * Common interface for leaf TODO nodes.
 *
 * Implemented by both local TODO nodes ({@code TodoItemNode}) and remote TODO nodes
 * ({@code TodoRemoteItemNode}) to provide unified navigation and selection logic.
 */
interface LeafTodoItemNode {
  fun getVirtualFile() : VirtualFile
  fun getNavigationOffset() : Int
  fun createNavigatable(project: @NotNull Project) : Navigatable
}