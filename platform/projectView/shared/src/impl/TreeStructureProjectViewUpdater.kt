// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.ide.CopyPasteUtil
import com.intellij.ide.bookmark.BookmarksListener
import com.intellij.ide.bookmark.FileBookmarksListener
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.scratch.RootType
import com.intellij.ide.ui.VirtualFileAppearanceListener
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.problems.ProblemListener
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.tree.project.ProjectFileNode
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class TreeStructureProjectViewUpdater(
  private val project: Project,
) : ProjectViewUpdater {
  override suspend fun continuouslyUpdatePane(pane: ProjectViewPaneModel) {
    UpdateSession(project, pane as TreeStructureBasedProjectViewPaneModel).continuouslyUpdatePane()
  }
}

private class UpdateSession(
  private val project: Project,
  private val model: TreeStructureBasedProjectViewPaneModel,
) {
  private val events = Channel<UpdateEvent>(capacity = Channel.UNLIMITED)

  suspend fun continuouslyUpdatePane() {
    coroutineScope {
      val scope = this
      val connection = project.messageBus.connect(scope)
      val disposable = scope.asDisposable()

      connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
          emitFromRoot()
        }
      })

      connection.subscribe(AdditionalLibraryRootsListener.TOPIC, AdditionalLibraryRootsListener { _, _, _, _ ->
        emitFromRoot()
      })

      connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun after(vfsEvents: List<VFileEvent>) {
          for (event in vfsEvents) {
            when (event) {
              is VFileCreateEvent -> {
                emitStructural(event.parent)
              }
              is VFileCopyEvent -> {
                emitStructural(event.newParent)
              }
              is VFileMoveEvent -> {
                emitStructural(event.newParent)
                emitStructural(event.oldParent)
                emitStructural(event.file)
              }
              is VFileDeleteEvent -> {
                emitStructural(event.file.parent)
                emitStructural(event.file)
              }
              else -> {
                emitStructural(event.file)
              }
            }
          }
        }
      })

      connection.subscribe(VirtualFileAppearanceListener.TOPIC, VirtualFileAppearanceListener { virtualFile ->
        emitStructural(virtualFile)
      })

      RootType.ROOT_EP.addChangeListener(scope) {
        emitFromRoot()
      }

      PsiManager.getInstance(project).addPsiTreeChangeListenerBackgroundable(object : PsiTreeChangeAdapter() {
        override fun childAdded(event: PsiTreeChangeEvent) {
          if (event.newChild is PsiWhiteSpace) return // optimization
          childrenChanged(event)
        }

        override fun childRemoved(event: PsiTreeChangeEvent) {
          if (event.oldChild is PsiWhiteSpace) return // optimization
          childrenChanged(event)
        }

        override fun childReplaced(event: PsiTreeChangeEvent) {
          if (event.oldChild is PsiWhiteSpace && event.newChild is PsiWhiteSpace) return // optimization
          childrenChanged(event)
        }

        override fun childrenChanged(event: PsiTreeChangeEvent) {
          emitPsiChange(event.parent)
        }

        override fun childMoved(event: PsiTreeChangeEvent) {
          emitPsiChange(event.oldParent)
          emitPsiChange(event.newParent)
        }
      }, disposable)

      connection.subscribe(BookmarksListener.TOPIC, FileBookmarksListener { file ->
        // a file's bookmark may affect its children too (e.g. Show Members), a directory's doesn't
        emitFileChanged(file, deep = !file.isDirectory)
      })

      FileStatusManager.getInstance(project).addFileStatusListener(object : FileStatusListener {
        override fun fileStatusesChanged() {
          emitAllPresentations()
        }

        override fun fileStatusChanged(file: VirtualFile) {
          emitFileChanged(file, false)
        }
      }, disposable)

      CopyPasteUtil.addDefaultListener(disposable) { element ->
        emitElementChanged(element, deep = false)
      }

      connection.subscribe(ProblemListener.TOPIC, object : ProblemListener {
        override fun problemsAppeared(file: VirtualFile) {
          emitPresentationsFromRootTo(file)
        }

        override fun problemsDisappeared(file: VirtualFile) {
          emitPresentationsFromRootTo(file)
        }
      })

      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
        // deep = true because files may have children too, e.g. with Show Members, and children inherit file colors
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
          emitFileChanged(file, true)
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          emitFileChanged(file, true)
        }
      })

      launch(CoroutineName("Project View updates consumer")) {
        processEvents()
      }
    }
  }

  private fun emitFromRoot() {
    events.trySend(FromRoot)
  }

  private fun emitAllPresentations() {
    events.trySend(AllPresentations)
  }

  private fun emitPresentationsFromRootTo(file: VirtualFile) {
    events.trySend(PresentationsFromRootTo(file))
  }

  private fun emitElementChanged(element: PsiElement, deep: Boolean) {
    events.trySend(ElementChanged(SmartPointerManager.createPointer(element), deep))
  }

  private fun emitFileChanged(file: VirtualFile, deep: Boolean) {
    events.trySend(FileChanged(file, deep = deep))
  }

  private fun emitStructural(file: VirtualFile?) {
    if (file == null) return
    events.trySend(StructuralChange(file))
  }

  private fun emitPsiChange(element: PsiElement?) {
    if (element == null) return
    val file = PsiUtilCore.getVirtualFile(element)
    if (file != null) {
      events.trySend(StructuralChange(file))
    }
    else {
      emitElementChanged(element, deep = true)
    }
  }

  private suspend fun processEvents() {
    for (first in events) {
      // Coalesce a burst: wait a little, then drain everything that has accumulated.
      delay(BATCH_DELAY)
      val batch = ArrayList<UpdateEvent>()
      batch.add(first)
      while (true) {
        val next = events.tryReceive().getOrNull() ?: break
        batch.add(next)
      }
      process(batch)
    }
  }

  private suspend fun process(batch: List<UpdateEvent>) {
    // A root refresh supersedes everything else in the batch.
    if (batch.any { it is FromRoot }) {
      updateAll()
      return
    }

    val structuralFiles = HashSet<VirtualFile>()
    val deepFiles = HashSet<VirtualFile>()
    val presentationFiles = HashSet<VirtualFile>()
    val problemFiles = HashSet<VirtualFile>()
    val elements = ArrayList<ElementChanged>()
    var allPresentations = false

    for (event in batch) {
      when (event) {
        FromRoot -> {} // handled above
        is StructuralChange -> structuralFiles.add(event.file)
        is FileChanged -> (if (event.deep) deepFiles else presentationFiles).add(event.file)
        is ElementChanged -> elements.add(event)
        AllPresentations -> allPresentations = true
        is PresentationsFromRootTo -> problemFiles.add(event.file)
      }
    }

    if (allPresentations) {
      updateAllPresentations()
    }

    // Structural changes are reduced to their containing project-area directories, like the old updater did:
    // a created/removed file has no node yet, so we reload the parent directory instead.
    deepFiles.addAll(reduceToAreaDirs(structuralFiles))
    updateByFiles(deepFiles, presentationFiles)

    for (file in problemFiles) {
      updatePresentationsFromRootTo(file)
    }
    for ((pointer, deep) in elements) {
      updateByElement(pointer, deep)
    }
  }

  private suspend fun updateAll() {
    // visitTree starts at the (single) root, so a deep update of it reloads the whole loaded tree.
    model.visitTree { node ->
      model.updateNode(node.id) { it.deep = true }
      TreeVisitor.Action.SKIP_CHILDREN
    }
  }

  private suspend fun updateAllPresentations() {
    model.visitTree { node ->
      model.updateNode(node.id) { it.deep = false }
      TreeVisitor.Action.CONTINUE
    }
  }

  private suspend fun updateByFiles(deepFiles: Set<VirtualFile>, presentationFiles: Set<VirtualFile>) {
    if (deepFiles.isEmpty() && presentationFiles.isEmpty()) return
    model.visitTree { node ->
      val treeNode = node.userObject.elementDescriptor as? AbstractTreeNode<*>
                     ?: return@visitTree TreeVisitor.Action.CONTINUE
      readAction {
        var descend = false
        for (file in deepFiles) {
          if (matchesFile(treeNode, file)) {
            model.updateNode(node.id) { it.deep = true }
            descend = true
          }
          else if (containsFile(treeNode, file)) {
            descend = true
          }
        }
        for (file in presentationFiles) {
          if (matchesFile(treeNode, file)) {
            model.updateNode(node.id) { it.deep = false }
            descend = true
          }
          else if (containsFile(treeNode, file)) {
            descend = true
          }
        }
        if (descend) {
          TreeVisitor.Action.CONTINUE
        }
        else {
          TreeVisitor.Action.SKIP_CHILDREN
        }
      }
    }
  }

  private suspend fun updatePresentationsFromRootTo(file: VirtualFile) {
    // Find the first valid ancestor (the file itself may already be removed).
    var target: VirtualFile? = file
    while (target != null && !target.isValid) {
      target = target.parent
    }
    val validTarget = target ?: return
    model.visitTree { node ->
      val treeNode = node.userObject.elementDescriptor as? AbstractTreeNode<*>
                     ?: return@visitTree TreeVisitor.Action.CONTINUE
      readAction {
        when {
          matchesFile(treeNode, validTarget) -> {
            model.updateNode(node.id) { it.deep = true }
            TreeVisitor.Action.CONTINUE
          }
          containsFile(treeNode, validTarget) -> {
            model.updateNode(node.id) { it.deep = false } // refresh the presentation along the path
            TreeVisitor.Action.CONTINUE
          }
          else -> TreeVisitor.Action.SKIP_CHILDREN
        }
      }
    }
  }

  private suspend fun updateByElement(pointer: SmartPsiElementPointer<PsiElement>, deep: Boolean) {
    model.visitTree { node ->
      val treeNode = node.userObject.elementDescriptor as? AbstractTreeNode<*>
                     ?: return@visitTree TreeVisitor.Action.CONTINUE
      readAction {
        val element = pointer.dereference() ?: return@readAction TreeVisitor.Action.INTERRUPT
        when {
          matchesElement(treeNode, element) -> {
            model.updateNode(node.id) { it.deep = deep }
            TreeVisitor.Action.CONTINUE
          }
          containsElement(treeNode, element) -> TreeVisitor.Action.CONTINUE
          else -> TreeVisitor.Action.SKIP_CHILDREN
        }
      }
    }
  }

  private suspend fun reduceToAreaDirs(files: Set<VirtualFile>): Set<VirtualFile> {
    if (files.isEmpty()) return emptySet()
    return readAction {
      val result = HashSet<VirtualFile>()
      for (file in files) {
        if (!file.isValid) continue
        val dir = if (file.isDirectory) file else file.parent
        if (dir != null && ProjectFileNode.findArea(dir, project) != null) {
          result.add(dir)
        }
      }
      result
    }
  }

  @RequiresReadLock
  private fun matchesFile(node: AbstractTreeNode<*>, file: VirtualFile): Boolean {
    return node.canRepresent(file)
  }

  @RequiresReadLock
  private fun containsFile(node: AbstractTreeNode<*>, file: VirtualFile): Boolean {
    if (node is ProjectViewNode<*> && file.isValid && node.contains(file)) return true
    val content = (node.value as? PsiElement)?.let { PsiUtilCore.getVirtualFile(it) } ?: return false
    return VfsUtilCore.isAncestor(content, file, true)
  }

  @RequiresReadLock
  private fun matchesElement(node: AbstractTreeNode<*>, element: PsiElement): Boolean {
    return node.canRepresent(element)
  }

  @RequiresReadLock
  private fun containsElement(node: AbstractTreeNode<*>, element: PsiElement): Boolean {
    if (node is ProjectViewNode<*>) {
      val file = PsiUtilCore.getVirtualFile(element)
      if (file != null && file.isValid && node.contains(file)) return true
    }
    val content = node.value as? PsiElement ?: return false
    return PsiTreeUtil.isAncestor(content, element, true)
  }
}

private val BATCH_DELAY = 10.milliseconds

private sealed interface UpdateEvent

private data object FromRoot : UpdateEvent

private data class StructuralChange(val file: VirtualFile) : UpdateEvent

private data class FileChanged(val file: VirtualFile, val deep: Boolean) : UpdateEvent

private data class ElementChanged(val pointer: SmartPsiElementPointer<PsiElement>, val deep: Boolean) : UpdateEvent

private data object AllPresentations : UpdateEvent

private data class PresentationsFromRootTo(val file: VirtualFile) : UpdateEvent
