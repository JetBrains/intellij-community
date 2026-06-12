// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.editor.impl.multiverse.createCodeInsightContextPresentation
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.tree.LeafState

internal class ProblemsContextNode(
  val parent: FileNode,
  val contextGroup: CodeInsightContext,
  val problems: Collection<Problem>,
  val groupByToolId: Boolean,
) : Node(parent) {
  private val presentation = createCodeInsightContextPresentation(contextGroup, parent.project)

  override fun getLeafState(): LeafState = LeafState.NEVER

  override fun getName(): String =
    presentation.text

  override fun update(project: Project, presentation: PresentationData) {
    presentation.addText(name, REGULAR_ATTRIBUTES)
    presentation.setIcon(this.presentation.icon)
  }

  override fun getChildren(): List<Node> =
    ProblemsViewHighlightingChildrenBuilder.prepareChildrenWithToolIdGroupingIfEnabled(
      problems = problems,
      groupByToolId = groupByToolId,
      parent = this,
      virtualFile = parent.file,
      groupNodeBuilder = { problems, group, parent -> ProblemsContextGroupNode(this, group, problems) }
    )

  override fun hashCode(): Int = contextGroup.hashCode()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? ProblemsContextNode ?: return false
    return that.contextGroup == contextGroup
  }
}
