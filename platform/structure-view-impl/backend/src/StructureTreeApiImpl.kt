// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.backend

import com.intellij.ide.rpc.FileEditorId
import com.intellij.ide.rpc.fileEditor
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.ide.structureView.newStructureView.StructureViewSelectVisitorState
import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.FileStructurePopup.logFileStructureCheckboxClick
import com.intellij.ide.util.FileStructurePopup.saveState
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.smartTree.Filter
import com.intellij.ide.util.treeView.smartTree.NodeProvider
import com.intellij.ide.util.treeView.smartTree.ProvidingTreeModel
import com.intellij.ide.util.treeView.smartTree.TreeElementWrapper
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.structureView.backend.BackendStructureTreeService.Companion.processStateToGetSelectedValue
import com.intellij.platform.structureView.backend.BackendStructureTreeService.Companion.visit
import com.intellij.platform.structureView.backend.BackendStructureTreeService.StructureViewEvent
import com.intellij.platform.structureView.impl.DelegatingNodeProvider
import com.intellij.platform.structureView.impl.StructureTreeApi
import com.intellij.platform.structureView.impl.dto.StructureViewDtoId
import com.intellij.platform.structureView.impl.dto.StructureViewModelDto
import com.intellij.util.application
import com.intellij.util.ui.tree.TreeUtil
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.asDeferred
import javax.swing.tree.TreePath
import kotlin.collections.firstOrNull
import kotlin.collections.get
import kotlin.collections.plus

internal class StructureTreeApiImpl : StructureTreeApi {
  override suspend fun createStructureViewModel(
    id: StructureViewDtoId,
    fileEditorId: FileEditorId,
    fileId: VirtualFileId,
    projectId: ProjectId,
  ): StructureViewModelDto? {
    val project = projectId.findProject()
    val file = fileId.virtualFile()
    if (file == null) {
      return null
    }
    val fileEditor = fileEditorId.fileEditor() ?: FileEditorManager.getInstance(project).getSelectedEditor(file) ?: return null

    return getStructureTreeService().createStructureViewModel(id, project, fileEditor, null)
  }

  override suspend fun structureViewModelDisposed(id: StructureViewDtoId) {
    getStructureTreeService().disposeStructureViewModel(id)
  }

  override suspend fun setTreeActionState(
    id: StructureViewDtoId,
    actionName: String,
    isEnabled: Boolean,
    autoClicked: Boolean,
  ) {
    getStructureTreeService().getStructureViewEntry(id)?.let { entry ->
      entry.structureTreeModel.invoker.invoke {
        val nodeProviders = (entry.treeModel as? ProvidingTreeModel)?.nodeProviders?.filterIsInstance<FileStructureNodeProvider<*>>() ?: emptyList()
        val actions = nodeProviders + entry.treeModel.sorters + entry.treeModel.filters
        val action = actions.firstOrNull { it.name == actionName } ?: run {
          logger.error("Action $actionName not found in structure model with id: $id")
          return@invoke
        }

        logFileStructureCheckboxClick(entry.fileEditor, entry.project, action)

        // Store autoclicked state in the action owner, persist user-initiated changes
        if (autoClicked) {
          entry.backendActionOwner.setAutoclickedActionState(actionName, isEnabled)
        }
        else {
          entry.backendActionOwner.clearAutoclickedActionState(actionName)
          saveState(action, isEnabled)
        }

        if (action is Filter || action is NodeProvider<*> && action !is DelegatingNodeProvider<*>) return@invoke

        entry.wrapper.rebuildTree()
        entry.structureTreeModel.invalidateAsync().thenRun {
          if (entry.structureTreeModel.isDisposed) return@thenRun
          entry.requestFlow.tryEmit(StructureViewEvent.ComputeNodes)
        }
      }
    }
  }

  @TestOnly
  override suspend fun getNewSelection(id: StructureViewDtoId): Int? {
    val entry = getStructureTreeService().getStructureViewEntry(id) ?: return null

    val selection = entry.structureTreeModel.invoker.compute {
      val (currentEditorElement, editorOffset) = entry.treeModel.currentEditorElement to ((entry.fileEditor as? TextEditor)?.getEditor()
                                                                                            ?.getCaretModel()?.offset ?: -1)
      val state = StructureViewSelectVisitorState()
      val root = entry.structureTreeModel.root ?: return@compute null
      visit(root, entry.structureTreeModel, TreePath(root)) {
        StructureViewComponent.visitPathForElementSelection(it, currentEditorElement, editorOffset, state)
        false
      }


      val selectedValue = processStateToGetSelectedValue(state, entry, currentEditorElement)
      entry.nodeToId[selectedValue]
    }.asDeferred().await()


    return selection
  }

  override suspend fun navigateToElement(id: StructureViewDtoId, elementId: Int): Boolean {
    val entry = getStructureTreeService().getStructureViewEntry(id) ?: return false

    val elementValue = entry.nodeToId.entries.find { it.value == elementId }?.key ?: return false

    val targetElement = entry.structureTreeModel.invoker.compute {
      var targetElement: StructureViewTreeElement? = null

      val root = entry.structureTreeModel.root ?: return@compute null
      visit(root, entry.structureTreeModel, TreePath(root)) {
        val wrapper = TreeUtil.getUserObject(it.lastPathComponent) as? TreeElementWrapper
        val element = wrapper?.getValue() as? StructureViewTreeElement
        return@visit if (element?.value == elementValue) {
          targetElement = element
          true
        }
        else {
          false
        }
      }
      targetElement
    }.asDeferred().await()


    if (targetElement == null) return false

    //todo put file in some(?) data context for navigation
    return withContext(Dispatchers.EDT) {
      var succeeded = false
      WriteIntentReadAction.run {
        CommandProcessor.getInstance().executeCommand(entry.project, {
          if (targetElement.canNavigateToSource()) {
            targetElement.navigate(true)
            entry.navigationCallback?.invoke(targetElement as AbstractTreeNode<*>)
            succeeded = true
          }
          IdeDocumentHistory.getInstance(entry.project).includeCurrentCommandAsNavigation()
        }, LangBundle.message("command.name.navigate"), null)
      }
      succeeded
    }
  }

  private fun getStructureTreeService(): BackendStructureTreeService {
    return application.service<BackendStructureTreeService>()
  }

  companion object {
    private val logger = logger<StructureTreeApiImpl>()
  }
}

@ApiStatus.Internal
class StructureTreeApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<StructureTreeApi>()) {
      StructureTreeApiImpl()
    }
  }
}