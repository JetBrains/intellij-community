@file:ApiStatus.Experimental
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.util.treeView.ExpandOnDoubleClickSupport
import com.intellij.ui.treeStructure.TreeNodePresentation
import com.intellij.ui.treeStructure.TreeNodePresentationBuilder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun <T : Any> buildProjectViewNodeModel(id: Long, userObject: T, build: (ProjectViewNodeModelBuilder) -> Unit): BackendProjectViewNodeModel<T> {
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
}

@ApiStatus.Experimental
sealed interface ProjectViewNodeModel : ExpandOnDoubleClickSupport {
  val id: Long
  val presentation: TreeNodePresentation
  fun canNavigate(): Boolean
  fun canNavigateToSource(): Boolean
  fun isIncludedInExpandAll(): Boolean
  fun isDirectory(): Boolean
}

@ApiStatus.Experimental
sealed interface BackendProjectViewNodeModel<out T> : ProjectViewNodeModel {
  val userObject: T
}
