// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

class SimpleTreeDiffRequestProcessor(
  project: Project,
  place: String,
  tree: ChangesTree,
  parentDisposable: Disposable
) : TreeHandlerDiffRequestProcessor(place, tree, DefaultChangesTreeDiffPreviewHandler) {
  init {
    Disposer.register(parentDisposable, this)
    TreeHandlerChangesTreeTracker(tree, this, handler).track()
  }
}
