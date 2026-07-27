// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.psi.PsiElement
import com.intellij.ui.tree.TreeVisitor
import org.jetbrains.annotations.ApiStatus

/**
 * Creates [ProjectViewSelectNodeVisitor]s used to locate the node to select in response to
 * [com.intellij.ide.IdeView.selectElement].
 *
 * A separate visitor is created for a given ([element][PsiElement], [file][VirtualFile]) pair,
 * so a provider is stateless and reusable.
 */
@ApiStatus.Experimental
interface ProjectViewSelectNodeVisitorProvider<T> {
  fun createSelectNodeVisitor(element: PsiElement?, file: VirtualFile?): ProjectViewSelectNodeVisitor<T>
}

/**
 * Decides, for every node of the currently loaded tree, how the traversal that looks for the node representing
 * [element]/[file] should proceed:
 * - [TreeVisitor.Action.INTERRUPT] - this node represents the element/file, it's the one to select;
 * - [TreeVisitor.Action.CONTINUE] - the element/file may be somewhere below, keep descending;
 * - [TreeVisitor.Action.SKIP_CHILDREN] - the element/file can't be in this subtree;
 * - [TreeVisitor.Action.SKIP_SIBLINGS] - the element is no longer valid, abort the search.
 */
@ApiStatus.Experimental
abstract class ProjectViewSelectNodeVisitor<T>(
  protected val element: PsiElement?,
  protected val file: VirtualFile?,
) {
  abstract suspend fun visitNodeForSelect(node: BackendProjectViewNodeModel<T>): TreeVisitor.Action
}
