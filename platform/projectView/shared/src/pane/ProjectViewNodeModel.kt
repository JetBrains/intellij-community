@file:ApiStatus.Experimental
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.util.treeView.ExpandOnDoubleClickSupport
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.treeStructure.TreeNodePresentationBuilder
import com.intellij.ui.treeStructure.TreeNodeWithPresentation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun <T : ProjectViewNodeUserObject> buildProjectViewNodeModel(id: Long, userObject: T, build: (ProjectViewNodeModelBuilder) -> Unit): BackendProjectViewNodeModel<T> {
  val builder = ProjectViewNodeModelBuilderImpl(id, userObject)
  build(builder)
  return builder.build()
}

@ApiStatus.Experimental
sealed interface ProjectViewNodeModelBuilder {
  fun setModel(model: BackendProjectViewNodeModel<*>)
  fun buildPresentation(build: (TreeNodePresentationBuilder) -> Unit)
  fun setPathElementType(pathElementType: String)
  fun setPathElementId(pathElementId: String)
  fun setCanNavigate(canNavigate: Boolean)
  fun setCanNavigateToSource(canNavigateToSource: Boolean)
  fun setIncludedInExpandAll(includedInExpandAll: Boolean)
  fun setIsDirectory(isDirectory: Boolean)
  fun setExpandOnDoubleClick(isExpandOnDoubleClick: Boolean)
  fun setShouldBeInitiallyExpanded(shouldBeInitiallyExpanded: Boolean)
  fun setIsSynthetic(isSynthetic: Boolean)
}

@ApiStatus.Experimental
sealed interface ProjectViewNodeModel : ExpandOnDoubleClickSupport, TreeNodeWithPresentation, Queryable {
  val id: Long
  fun canNavigate(): Boolean
  fun canNavigateToSource(): Boolean
  fun isIncludedInExpandAll(): Boolean
  fun isDirectory(): Boolean
  fun shouldBeInitiallyExpanded(): Boolean
  fun isSynthetic(): Boolean
}

@ApiStatus.Experimental
sealed interface BackendProjectViewNodeModel<out T : ProjectViewNodeUserObject> : ProjectViewNodeModel {
  val userObject: T
}

/**
 * The domain backend-only part of a Project View node.
 *
 * By "backend-only" it is meant that it only exists within the process that provides data to the Project View.
 * In the full remote development mode it's the backend, in the light mode it can be the frontend.
 *
 * User objects of the children of a given node must be unique.
 * It's not required that they should be globally unique.
 * That is, two nodes with equal user objects can coexist within the same tree
 * as long as they're children of different nodes: for example, a single directory
 * can appear twice under different subsections, and share all of its children.
 */
@ApiStatus.Experimental
interface ProjectViewNodeUserObject {

  /**
   * Some name to identify the node.
   *
   * It's usually the same as the presentation's main text, but it's not required.
   * It must be fast to access and must not change.
   * Unlike the presentation, it must be available before the node model is computed.
   * It must not be used in `equals` or `hashCode` (to enable tracking identities of renamed nodes).
   *
   * Node names are used to find the matching nodes when a node's children are updated.
   * If there's a new node with the same name, it's treated like an update rather than removal and addition, to reduce UI flickering.
   * If there are several nodes with the same name, the match will be arbitrary, but it'll work.
   */
  val name: String

  /**
   * The virtual file associated with the node, if any.
   */
  fun getVirtualFile(): VirtualFile? = null
}

/**
 * A frontend-only representation of the backend's user object.
 *
 * The DTO class is not used directly because it's a data class (easier to serialize),
 * but we don't want any data class functions here.
 * Especially not `equals` and `hashCode`, as we can't make them work properly.
 * We simply don't have enough data on the frontend for that, as the real user object is gone.
 */
internal class FrontendProjectViewNodeUserObject(
  override val name: String, // not used on the frontend currently, but is useful for debugging
  val virtualFileId: VirtualFileId?,
) : ProjectViewNodeUserObject {
  override fun getVirtualFile(): VirtualFile? = virtualFileId?.virtualFile()

  override fun toString(): String {
    return "FrontendProjectViewNodeUserObject(name='$name', virtualFileId=$virtualFileId)"
  }
}
