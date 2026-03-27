// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.ui

import com.intellij.analysis.problemsView.toolWindow.Node
import com.intellij.analysis.problemsView.toolWindow.ProblemNodeI
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.tree.LeafState
import java.util.Objects.hash

internal class AIReviewProblemNode(
  parent: Node,
  internal val file: VirtualFile,
  override val problem: AIReviewFileProblem,
) : Node(parent), ProblemNodeI {

  init {
    logger<AIReviewProblemNode>().assertTrue(project != null, this)
  }

  private var text: @NlsSafe String = ""
  private var lineStart: Int = 0
  private var lineEnd: Int = 0
  private var column: Int = 0
  private var severity: Int = 0

  override fun getText(): String = text
  override fun getLine(): Int = lineStart
  override fun getColumn(): Int = column
  override fun getSeverity(): Int = severity

  override val descriptor: OpenFileDescriptor
    get() = OpenFileDescriptor(project!!, file, lineStart, column)

  override fun getLeafState(): LeafState = LeafState.DEFAULT

  override fun getName(): String = text

  override fun getChildren(): Collection<Node> = listOf(AIReviewDescriptionNode(project, this))

  override fun getVirtualFile(): VirtualFile = file

  override fun getNavigatable(): Navigatable = descriptor

  override fun update(project: Project, presentation: PresentationData) {
    text = problem.text
    lineStart = problem.line
    val lineStartWithOffset = lineStart + 1
    lineEnd = problem.lineEnd
    val lineEndWithOffset = lineEnd + 1
    column = problem.column
    severity = problem.severity.ordinal
    presentation.addText(text, REGULAR_ATTRIBUTES)
    presentation.setIcon(problem.icon)
    if (lineStart >= 0) presentation.addText(" :$lineStartWithOffset", GRAYED_ATTRIBUTES)

    if (lineEnd >= 0 && lineStartWithOffset != lineEndWithOffset) {
      presentation.addText(" - $lineEndWithOffset", GRAYED_ATTRIBUTES)
    }
  }

  override fun hashCode(): Int = hash(project, problem)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? AIReviewProblemNode ?: return false
    return that.project == project && that.problem == problem
  }
}
