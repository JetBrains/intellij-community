// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.ide.projectView.impl.nodes.DropTargetNode
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.platform.projectView.pane.BackendProjectViewPaneStateAccessor
import com.intellij.platform.projectView.pane.ProjectViewDnDOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneDnDHandler
import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.refactoring.RefactoringActionHandlerFactory
import com.intellij.refactoring.copy.CopyHandler
import com.intellij.refactoring.move.MoveHandler
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.map2Array
import com.intellij.util.containers.nullize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.dnd.DnDConstants
import javax.swing.tree.DefaultMutableTreeNode

internal object NoOpDnDHandler : ProjectViewPaneDnDHandler {
  override suspend fun performInternalDnD(
    sourceIDs: List<Long>,
    targetID: Long,
    options: ProjectViewDnDOptions,
  ) {
  }
}

internal class DefaultProjectViewPaneDnDHandler<T>(
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
  
  private fun createHandler(options: ProjectViewDnDOptions): DropHandler? {
    return when (options.action.actionId) {
      DnDConstants.ACTION_COPY -> CopyDropHandler()
      DnDConstants.ACTION_COPY_OR_MOVE, DnDConstants.ACTION_MOVE -> MoveDropHandler()
      else -> null
    }
  }

  @RequiresReadLock
  private fun createSource(node: BackendProjectViewNodeModel<T>): Source<T>? {
    val psiElement = psi.extractPsiElements(listOf(node)).singleOrNull() ?: return null
    return Source(node, psiElement.createSmartPointer())
  }

  @RequiresReadLock
  private fun createTarget(node: BackendProjectViewNodeModel<T>): Target<T>? {
    val psiElement = psi.extractPsiElements(listOf(node)).singleOrNull() ?: return null
    val targetModule = if (pane.project.isInitialized) ModuleUtilCore.findModuleForPsiElement(psiElement) else null
    return Target(node, psiElement.createSmartPointer(), targetModule)
  }
  
  private data class DnDContext<T>(
    val sources: List<Source<T>>,
    val target: Target<T>,
  )
  
  private data class Source<T>(val node: BackendProjectViewNodeModel<T>, val psi: SmartPsiElementPointer<PsiElement>) {
    @get:RequiresReadLock
    val psiElement: PsiElement?
      get() = psi.dereference()
  }

  private data class Target<T>(val node: BackendProjectViewNodeModel<T>, val psi: SmartPsiElementPointer<PsiElement>, val module: Module?) {
    @get:RequiresReadLock
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
      val targetDirectory =
        when (targetElement) {
          is PsiDirectoryContainer -> {
            targetElement.directories.firstOrNull()
          }
          is PsiDirectory -> {
            targetElement
          }
          else -> {
            targetElement.containingFile?.containingDirectory
          }
        } ?: return
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
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
        if (!project.isInitialized) return
        val targetElement = target.psiElement
        if (targetElement?.isValid != true) return
        val sourceElements = sources.map { it.psiElement }.filter { it?.isValid == true }.nullize() ?: return
        val targetModule = target.module
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        RefactoringActionHandlerFactory.getInstance().createMoveHandler().invoke(
          project,
          sourceElements.toTypedArray(),
          CustomizedDataContext.withSnapshot(dataContext) { sink ->
            sink[LangDataKeys.TARGET_MODULE] = targetModule
            sink[LangDataKeys.TARGET_PSI_ELEMENT] = targetElement
          }
        )
      }
    }
  }
}

private fun BackendProjectViewNodeModel<*>.toTreeNode(): DefaultMutableTreeNode {
  return DefaultMutableTreeNode(userObject)
}
