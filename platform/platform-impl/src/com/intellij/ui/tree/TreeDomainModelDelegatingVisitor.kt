// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.openapi.application.EDT
import com.intellij.ui.treeStructure.TreeDomainModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.tree.TreePath

internal class TreeDomainModelDelegatingVisitor(
  private val model: TreeDomainModel,
  private val delegate: TreeVisitor,
) : SuspendingTreeVisitor {

  override suspend fun visit(path: TreePath): TreeVisitor.Action {
    if (delegate.visitThread() == TreeVisitor.VisitThread.EDT) {
      return actualVisitEdt(delegate, path) // For EDT visiting, the visitor does all three steps in visit().
    }
    else {
      val preVisitResult = preVisit(path, delegate)
      if (preVisitResult != null) return preVisitResult
      val visitResult = actualVisitBgt(delegate, path)
      val postVisitResult = postVisit(visitResult, path, delegate)
      return postVisitResult
    }
  }

  private suspend fun preVisit(path: TreePath, visitor: TreeVisitor): TreeVisitor.Action? =
    (visitor as? EdtBgtTreeVisitor)?.let {
      withContext(Dispatchers.EDT) {
        visitor.preVisitEDT(path)
      }
    }

  private suspend fun actualVisitEdt(
    visitor: TreeVisitor,
    path: TreePath,
  ): TreeVisitor.Action =
    withContext(Dispatchers.EDT) {
      visitor.visit(path)
    }

  private suspend fun actualVisitBgt(
    visitor: TreeVisitor,
    path: TreePath,
  ): TreeVisitor.Action =
    withContext(Dispatchers.Default) {
      model.accessData {
        visitor.visit(path)
      }
    }

  private suspend fun postVisit(action: TreeVisitor.Action, path: TreePath, visitor: TreeVisitor): TreeVisitor.Action =
    (visitor as? EdtBgtTreeVisitor)?.let {
      withContext(Dispatchers.EDT) {
        visitor.postVisitEDT(path, action)
      }
    } ?: action

  override fun toString(): String =
    "TreeDomainModelDelegatingVisitor(model=$model, delegate=$delegate)"
}
