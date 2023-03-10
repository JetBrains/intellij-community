// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.swing.tree.DefaultTreeModel

/**
 * Call [shutdown] when the tree is no longer needed.
 */
abstract class AsyncChangesTree : ChangesTree {
  protected abstract val changesTreeModel: AsyncChangesTreeModel

  val scope = CoroutineScope(SupervisorJob())

  private val _requests = MutableSharedFlow<Request>()
  private val _model = MutableStateFlow(Model(TreeModelBuilder.buildEmpty(), null))

  private val _busy = MutableStateFlow(false)
  val busy: Flow<Boolean> = _busy.asStateFlow()


  constructor(project: Project,
              showCheckboxes: Boolean,
              highlightProblems: Boolean)
    : this(project, showCheckboxes, highlightProblems, true)

  constructor(project: Project,
              showCheckboxes: Boolean,
              highlightProblems: Boolean,
              withSpeedSearch: Boolean)
    : super(project, showCheckboxes, highlightProblems, withSpeedSearch) {
    start()
  }

  fun shutdown() {
    scope.cancel()
  }


  override fun shouldShowBusyIconIfNeeded(): Boolean = true

  override fun rebuildTree() {
    requestRefresh()
  }

  override fun rebuildTree(treeStateStrategy: TreeStateStrategy<*>) {
    requestRefresh(treeStateStrategy)
  }


  fun requestRefresh() {
    requestRefreshImpl(treeStateStrategy = null)
  }

  fun requestRefresh(treeStateStrategy: TreeStateStrategy<*>) {
    requestRefreshImpl(treeStateStrategy = treeStateStrategy)
  }

  private fun requestRefreshImpl(treeStateStrategy: TreeStateStrategy<*>?) {
    val refreshGrouping = grouping
    scope.launch {
      _requests.emit(Request(refreshGrouping, treeStateStrategy))
    }
  }


  private fun start() {
    scope.launch {
      _requests.collectLatest { request ->
        handleRequest(request)
      }
    }

    val edtContext = Dispatchers.EDT + ModalityState.any().asContextElement()
    scope.launch(edtContext) {
      _busy.collectLatest {
        setPaintBusy(it)
      }
    }
    scope.launch(edtContext) {
      _model.collectLatest { model ->
        updateTreeModel(model)
      }
    }
  }

  private suspend fun handleRequest(request: Request) {
    _busy.value = true
    try {
      val treeModel = changesTreeModel.buildTreeModel(request.grouping)
      val model = Model(treeModel, request.treeStateStrategy)
      _model.value = model
    }
    finally {
      _busy.value = false
    }
  }

  @RequiresEdt
  private suspend fun updateTreeModel(model: Model) {
    coroutineToIndicator {
      val strategy = model.treeStateStrategy
      if (strategy != null) {
        updateTreeModel(model.treeModel, strategy)
      }
      else {
        updateTreeModel(model.treeModel)
      }
    }
  }

  private class Request(
    val grouping: ChangesGroupingPolicyFactory,
    val treeStateStrategy: TreeStateStrategy<*>?
  )

  private class Model(
    val treeModel: DefaultTreeModel,
    val treeStateStrategy: TreeStateStrategy<*>?
  )
}

interface AsyncChangesTreeModel {
  suspend fun buildTreeModel(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel
}
