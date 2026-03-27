// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.ui

import com.intellij.analysis.problemsView.toolWindow.Node
import com.intellij.analysis.problemsView.toolWindow.ProblemNodeI
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tree.LeafState
import java.util.Objects

internal class AIReviewDescriptionNode(project: Project, parentNode: ProblemNodeI) : Node(parentNode as Node), ProblemNodeI by parentNode {

  internal val description: String
    get() = problem.description.orEmpty()

  internal val htmlDescription: String =
    MarkdownToHtmlConverter().convertToHtml(project, parentNode.problem.description.orEmpty())

  override val descriptor: OpenFileDescriptor? get() = (parentDescriptor as Node).descriptor

  override fun getVirtualFile(): VirtualFile? = (parentDescriptor as Node).getVirtualFile()

  override fun getName(): String = description

  override fun getLeafState(): LeafState = LeafState.ALWAYS

  override fun update(project: Project, presentation: PresentationData) {}

  override fun hashCode(): Int = Objects.hash(project, description)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? AIReviewDescriptionNode ?: return false
    return that.project == project && that.description == description
  }
}
