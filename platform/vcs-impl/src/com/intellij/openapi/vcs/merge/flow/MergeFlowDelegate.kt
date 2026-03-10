// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge.flow

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.tree.DefaultTreeModel

internal interface MergeFlowDelegate {
  fun createCenterPanel(): JComponent
  fun createActions(): List<Action>
  fun onTreeChanged(selectedFiles: List<VirtualFile>, unmergeableFileSelected: Boolean, unacceptableFileSelected: Boolean)
  fun buildTreeModel(project: Project?, grouping: ChangesGroupingPolicyFactory, unresolvedFiles: List<VirtualFile>): DefaultTreeModel
}