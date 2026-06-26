@file:ApiStatus.Experimental
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ui.treeStructure.TreeNodePresentation
import com.intellij.ui.treeStructure.TreeNodePresentationBuilder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
suspend fun buildProjectViewNodeModel(id: Long, build: suspend (ProjectViewNodeModelBuilder) -> Unit): ProjectViewNodeModel {
  val builder = ProjectViewNodeModelBuilderImpl(id)
  build(builder)
  return builder.build()
}

@ApiStatus.Experimental
sealed interface ProjectViewNodeModelBuilder {
  fun buildPresentation(build: (TreeNodePresentationBuilder) -> Unit)
  fun setCanNavigate(canNavigate: Boolean)
  fun setCanNavigateToSource(canNavigateToSource: Boolean)
  fun setIncludedInExpandAll(includedInExpandAll: Boolean)
  fun setIsDirectory(isDirectory: Boolean)
}

@ApiStatus.Experimental
sealed interface ProjectViewNodeModel {
  val id: Long
  val presentation: TreeNodePresentation
  fun canNavigate(): Boolean
  fun canNavigateToSource(): Boolean
  fun isIncludedInExpandAll(): Boolean
  fun isDirectory(): Boolean
}
