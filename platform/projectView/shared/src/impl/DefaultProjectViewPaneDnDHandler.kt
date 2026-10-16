// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.ide.projectView.impl.BackendProjectViewFileTransfer
import com.intellij.ide.projectView.impl.nodes.DropTargetNode
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.platform.projectView.pane.BackendProjectViewPaneStateAccessor
import com.intellij.platform.projectView.pane.ProjectViewDnDOptions
import com.intellij.platform.projectView.pane.ProjectViewExternalFile
import com.intellij.platform.projectView.pane.ProjectViewNodeUserObject
import com.intellij.platform.projectView.pane.ProjectViewPaneDnDHandler
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.RefactoringActionHandlerFactory
import com.intellij.refactoring.copy.CopyHandler
import com.intellij.refactoring.move.MoveHandler
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.map2Array
import com.intellij.util.containers.nullize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.dnd.DnDConstants
import java.nio.file.Path
import javax.swing.tree.DefaultMutableTreeNode

@RequiresReadLock
private fun getProjectViewTargetDirectory(element: PsiElement): PsiDirectory? {
  return when (element) {
    is PsiDirectoryContainer -> element.directories.firstOrNull()
    is PsiDirectory -> element
    else -> element.containingFile?.containingDirectory
  }
}

internal object NoOpDnDHandler : ProjectViewPaneDnDHandler {
  override suspend fun performInternalDnD(
    sourceIDs: List<Long>,
    targetID: Long,
    options: ProjectViewDnDOptions,
  ) {
  }
}

internal class DefaultProjectViewPaneDnDHandler<T : ProjectViewNodeUserObject>(
  private val pane: ProjectViewPaneModel,
  private val state: BackendProjectViewPaneStateAccessor<T>,
  private val psi: ProjectViewPsiExtractor<T>,
) : ProjectViewPaneDnDHandler {
  override suspend fun performInternalDnD(
    sourceIDs: List<Long>,
    targetID: Long,
    options: ProjectViewDnDOptions,
  ) {
    val handler = createHandler(options) ?: return
    val sourceNodes = sourceIDs.mapNotNull { state.getNodeById(it) }.nullize() ?: return
    val targetNode = state.getNodeById(targetID) ?: return
    val context = readAction ra@ {
      val sources = sourceNodes.mapNotNull { createSource(it) }.nullize() ?: return@ra null
      val target = createTarget(targetNode) ?: return@ra null
      val validTarget = handler.getValidTarget(sources, target) ?: return@ra null
      val validSources = handler.getValidSources(sources, target).nullize() ?: return@ra null
      DnDContext(validSources, validTarget)
    } ?: return
    withContext(Dispatchers.EDT) { // handlers, unfortunately, need both the WIL (because PSI) and the EDT (because dialogs)
      handler.drop(context.sources, context.target)
    }
  }
  
  override suspend fun receiveExternalDrop(
    files: List<ProjectViewExternalFile>,
    targetID: Long,
    options: ProjectViewDnDOptions,
  ) {
    val targetNode = state.getNodeById(targetID)
    if (targetNode == null) {
      LOG.debug { "External DnD: the target node $targetID is not found" }
      return
    }
    val clientFiles = files.filterIsInstance<ProjectViewExternalFile.FromClient>().map { it.file }
    if (clientFiles.isNotEmpty()) {
      val transfer = BackendProjectViewFileTransfer.getInstanceOrNull()
      val targetDirectory = readAction { createTarget(targetNode)?.psiElement?.let { getProjectViewTargetDirectory(it) }?.virtualFile }
      if (transfer == null) {
        LOG.debug { "External DnD: no file transfer service, the client files are ignored: $clientFiles" }
      }
      else if (targetDirectory == null) {
        LOG.debug { "External DnD: no target directory for the node $targetID" }
      }
      else {
        transfer.copyFromClient(clientFiles, targetDirectory, pane.project)
      }
    }
    val localPaths = files.filterIsInstance<ProjectViewExternalFile.Local>().map { it.path }
    if (localPaths.isNotEmpty()) {
      dropLocalFiles(localPaths, targetNode, options)
    }
  }

  override suspend fun sendDropToClient(sourceIDs: List<Long>) {
    val transfer = BackendProjectViewFileTransfer.getInstanceOrNull()
    if (transfer == null) {
      LOG.debug { "Send to client: no file transfer service" }
      return
    }
    val sourceNodes = sourceIDs.mapNotNull { state.getNodeById(it) }.nullize()
    if (sourceNodes == null) {
      LOG.debug { "Send to client: none of the source nodes $sourceIDs is found" }
      return
    }
    val files = readAction { psi.extractPsiElements(sourceNodes).filterIsInstance<PsiFile>().mapNotNull { it.virtualFile } }
    LOG.debug { "Send to client: the nodes $sourceIDs give the files $files" }
    transfer.sendToClient(files, pane.project)
  }

  private suspend fun dropLocalFiles(
    files: List<Path>,
    targetNode: BackendProjectViewNodeModel<T>,
    options: ProjectViewDnDOptions,
  ) {
    val virtualFiles = withContext(Dispatchers.IO) {
      files.mapNotNull { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
    }.nullize()
    if (virtualFiles == null) {
      LOG.debug { "External DnD: none of the dropped files is found: $files" }
      return
    }
    val context = readAction ra@ {
      val sources = virtualFiles.mapNotNull { PsiUtilCore.findFileSystemItem(pane.project, it)?.createSmartPointer() }.nullize()
      if (sources == null) {
        LOG.debug { "External DnD: no PSI for the dropped files $virtualFiles" }
        return@ra null
      }
      val target = createTarget(targetNode)
      if (target == null) {
        LOG.debug { "External DnD: no target PSI for the node ${targetNode.id}" }
        return@ra null
      }
      ExternalDnDContext(sources, target)
    } ?: return
    withContext(Dispatchers.EDT) {
      val sourceElements = context.sourceElements.nullize()
      if (sourceElements == null) {
        LOG.debug { "External DnD: the dropped files became invalid" }
        return@withContext
      }
      LOG.debug { "External DnD: ${options.action} of ${sourceElements.toList()} onto the node ${targetNode.id}" }
      when (options.action.actionId) {
        DnDConstants.ACTION_COPY -> copyLocalFiles(sourceElements, context.target)
        DnDConstants.ACTION_COPY_OR_MOVE, DnDConstants.ACTION_MOVE -> moveLocalFiles(sourceElements, context.target)
        else -> LOG.debug { "External DnD: the action ${options.action} is not supported" }
      }
    }
  }

  private fun copyLocalFiles(sourceElements: Array<PsiFileSystemItem>, target: Target<T>) {
    val targetDirectory = target.psiElement?.let { getProjectViewTargetDirectory(it) }
    if (targetDirectory == null) {
      LOG.debug { "External DnD: no target directory for the node ${target.node.id}" }
      return
    }
    CopyHandler.doCopy(sourceElements, targetDirectory)
  }

  private fun moveLocalFiles(sourceElements: Array<PsiFileSystemItem>, target: Target<T>) {
    val userObject = target.node.userObject
    if (userObject is DropTargetNode) {
      userObject.dropExternalFiles(sourceElements, pane.getDataContext(listOf(target.node.id)))
    }
    else {
      invokeMoveRefactoring(sourceElements.toList(), target, DataContext.EMPTY_CONTEXT)
    }
  }

  private fun invokeMoveRefactoring(sourceElements: List<PsiElement?>, target: Target<T>, dataContext: DataContext) {
    val project = pane.project
    if (!project.isInitialized) {
      LOG.debug { "Move: the project is not initialized" }
      return
    }
    val targetElement = target.psiElement
    if (targetElement?.isValid != true) {
      LOG.debug { "Move: the target of the node ${target.node.id} is invalid" }
      return
    }
    val validSourceElements = sourceElements.filter { it?.isValid == true }.nullize()
    if (validSourceElements == null) {
      LOG.debug { "Move: no valid source elements" }
      return
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    RefactoringActionHandlerFactory.getInstance().createMoveHandler().invoke(
      project,
      validSourceElements.toTypedArray(),
      CustomizedDataContext.withSnapshot(dataContext) { sink ->
        sink[LangDataKeys.TARGET_MODULE] = target.module
        sink[LangDataKeys.TARGET_PSI_ELEMENT] = targetElement
      }
    )
  }

  private data class ExternalDnDContext<T : ProjectViewNodeUserObject>(
    val sources: List<SmartPsiElementPointer<PsiFileSystemItem>>,
    val target: Target<T>,
  ) {
    @get:RequiresReadLock
    val sourceElements: Array<PsiFileSystemItem>
      get() = sources.mapNotNull { it.dereference() }.filter { it.isValid }.toTypedArray()
  }

  private fun createHandler(options: ProjectViewDnDOptions): DropHandler? {
    return when (options.action.actionId) {
      DnDConstants.ACTION_COPY -> CopyDropHandler()
      DnDConstants.ACTION_COPY_OR_MOVE, DnDConstants.ACTION_MOVE -> MoveDropHandler()
      else -> null
    }
  }

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  private fun createSource(node: BackendProjectViewNodeModel<T>): Source<T>? {
    val psiElement = psi.extractPsiElements(listOf(node)).singleOrNull() ?: return null
    return Source(node, psiElement.createSmartPointer())
  }

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  private fun createTarget(node: BackendProjectViewNodeModel<T>): Target<T>? {
    val psiElement = psi.extractPsiElements(listOf(node)).singleOrNull() ?: return null
    val targetModule = if (pane.project.isInitialized) ModuleUtilCore.findModuleForPsiElement(psiElement) else null
    return Target(node, psiElement.createSmartPointer(), targetModule)
  }
  
  private data class DnDContext<T : ProjectViewNodeUserObject>(
    val sources: List<Source<T>>,
    val target: Target<T>,
  )
  
  private data class Source<T : ProjectViewNodeUserObject>(val node: BackendProjectViewNodeModel<T>, val psi: SmartPsiElementPointer<PsiElement>) {
    @get:RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
    val psiElement: PsiElement?
      get() = psi.dereference()
  }

  private data class Target<T : ProjectViewNodeUserObject>(val node: BackendProjectViewNodeModel<T>, val psi: SmartPsiElementPointer<PsiElement>, val module: Module?) {
    @get:RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
    val psiElement: PsiElement?
      get() = psi.dereference()
  }

  private val Target<T>.parentTarget: Target<T>?
    get() {
      val parentNode = state.getParentByChildId(node.id) ?: return null
      return createTarget(parentNode)
    }

  private abstract inner class DropHandler {
    
    abstract fun drop(sources: List<Source<T>>, target: Target<T>)

    fun getValidTarget(sources: List<Source<T>>, target: Target<T>): Target<T>? {
      var validTarget: Target<T>? = target
      while (validTarget != null) {
        if (isValidTarget(sources, validTarget)) break
        if (!shouldDelegateToParent(sources, validTarget)) return null
        validTarget = target.parentTarget
      }
      return validTarget
    }

    fun getValidSources(sources: List<Source<T>>, target: Target<T>): List<Source<T>> {
      return sources.filter { isValidSource(it, target) }
    }

    abstract fun isValidTarget(sources: List<Source<T>>, target: Target<T>): Boolean

    abstract fun isValidSource(source: Source<T>, target: Target<T>): Boolean

    abstract fun shouldDelegateToParent(sources: List<Source<T>>, target: Target<T>): Boolean
  }
  
  private inner class CopyDropHandler : DropHandler() {
    override fun isValidTarget(
      sources: List<Source<T>>,
      target: Target<T>,
    ): Boolean {
      val targetElement = target.psiElement ?: return false
      val targetFile = targetElement.containingFile
      val isTargetAcceptable =
        targetElement is PsiDirectoryContainer ||
        targetElement is PsiDirectory ||
        targetFile?.containingDirectory != null
      return isTargetAcceptable && CopyHandler.canCopy(sources.map2Array { it.psiElement })
    }

    override fun shouldDelegateToParent(sources: List<Source<T>>, target: Target<T>): Boolean {
      val targetElement = target.psiElement
      return targetElement !is PsiDirectoryContainer && targetElement !is PsiDirectory
    }

    override fun isValidSource(
      source: Source<T>,
      target: Target<T>,
    ): Boolean {
      return true
    }

    override fun drop(
      sources: List<Source<T>>,
      target: Target<T>,
    ) {
      val targetElement = target.psiElement ?: return
      val targetDirectory = getProjectViewTargetDirectory(targetElement) ?: return
      CopyHandler.doCopy(sources.map2Array { it.psiElement }, targetDirectory)
    }
  }
  
  private inner class MoveDropHandler : DropHandler() {
    override fun isValidTarget(
      sources: List<Source<T>>,
      target: Target<T>,
    ): Boolean {
      val userObject = target.node.userObject
      if (userObject is DropTargetNode && userObject.canDrop(sources.map2Array { it.node.toTreeNode() })) return true
      val sourceElements = sources.map2Array { it.psiElement }
      return MoveHandler.canMove(sourceElements, target.psiElement)
    }

    override fun shouldDelegateToParent(sources: List<Source<T>>, target: Target<T>): Boolean {
      val targetElement = target.psiElement ?: return false
      return !MoveHandler.isValidTarget(targetElement, sources.mapNotNull { it.psiElement }.toTypedArray())
    }

    override fun isValidSource(
      source: Source<T>,
      target: Target<T>,
    ): Boolean {
      return target.node.id != source.node.id && !MoveHandler.isMoveRedundant(source.psiElement, target.psiElement)
    }

    override fun drop(
      sources: List<Source<T>>,
      target: Target<T>,
    ) {
      val userObject = target.node.userObject
      val dataContext = pane.getDataContext(sources.map { it.node.id })
      if (userObject is DropTargetNode) {
        userObject.drop(sources.map2Array { it.node.toTreeNode() }, dataContext)
      }
      else {
        invokeMoveRefactoring(sources.map { it.psiElement }, target, dataContext)
      }
    }
  }
}

private fun BackendProjectViewNodeModel<*>.toTreeNode(): DefaultMutableTreeNode {
  return DefaultMutableTreeNode(userObject)
}

private val LOG = logger<DefaultProjectViewPaneDnDHandler<*>>()
