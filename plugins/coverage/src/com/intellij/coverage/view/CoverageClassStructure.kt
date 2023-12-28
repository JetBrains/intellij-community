// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UNCHECKED_CAST")

package com.intellij.coverage.view

import com.intellij.coverage.analysis.JavaCoverageAnnotator
import com.intellij.coverage.analysis.PackageAnnotator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.DefaultMutableTreeNode


private val LOG = logger<CoverageClassStructure>()

data class CoverageNodeInfo(val id: String,
                            val name: String,
                            val value: PsiNamedElement,
                            val counter: PackageAnnotator.ClassCoverageInfo = PackageAnnotator.ClassCoverageInfo()) {
  override fun toString() = name
}


class CoverageClassStructure(val project: Project, val annotator: JavaCoverageAnnotator) : Disposable {
  private val fileStatusManager = FileStatusManager.getInstance(project)
  private val state = CoverageViewManager.getInstance(project).stateBean
  private val cache = hashMapOf<String, PsiNamedElement?>()

  var hasVCSFilteredChildren: Boolean = false
    private set
  var hasFullyCoveredChildren: Boolean = false
    private set
  private val nodeMap = hashMapOf<String, CoverageTreeNode>()

  init {
    buildClassesTree()
    state.addListener(this) { buildClassesTree() }
  }

  fun hasChildren(id: String): Boolean {
    val node = nodeMap[id] ?: return false
    return node.childCount > 0
  }


  fun getChildrenInfo(id: String): List<CoverageNodeInfo> {
    val node = nodeMap[id] ?: return emptyList()
    return node.children().toList().map { (it as CoverageTreeNode).userObject }
  }

  fun getNodeInfo(id: String): CoverageNodeInfo? = nodeMap[id]?.userObject


  private fun buildClassesTree() {
    val onlyModified = state.isShowOnlyModified
    val hideFullyCovered = state.isHideFullyCovered
    val flattenPackages = state.isFlattenPackages

    hasVCSFilteredChildren = false
    hasFullyCoveredChildren = false
    val classes = annotator.classesCoverage.mapNotNull { (fqn, counter) ->
      if (hideFullyCovered && counter.isFullyCovered) {
        hasFullyCoveredChildren = true
        null
      }
      else if (onlyModified && !isModified(fqn)) {
        hasVCSFilteredChildren = true
        null
      }
      else {
        val psiClass = getPsiClass(fqn) ?: return@mapNotNull null
        val simpleName = StringUtil.getShortName(fqn)
        CoverageNodeInfo(fqn, simpleName, psiClass, counter)
      }
    }

    val root = CoverageTreeNode(CoverageNodeInfo("", "", getPsiPackage("")!!))
    loop@ for (clazz in classes) {
      val packageName = StringUtil.getPackageName(clazz.id)
      if (flattenPackages) {
        root.userObject.counter.append(clazz.counter)
        val psiPackage = getPsiPackage(packageName)!!
        val node = root.getOrCreateChild(CoverageNodeInfo(packageName, packageName, psiPackage))
        node.userObject.counter.append(clazz.counter)
        node.getOrCreateChild(clazz)
      }
      else {
        var node = root
        if (packageName.isNotEmpty()) {
          for (part in packageName.split('.')) {
            node.userObject.counter.append(clazz.counter)
            val newId = if (node.userObject.id.isEmpty()) part else "${node.userObject.id}.$part"
            val psiPackage = getPsiPackage(newId)
            if (psiPackage == null) {
              LOG.warn("Failed to locate package $newId, skip it in coverage results")
              continue@loop
            }
            node = node.getOrCreateChild(CoverageNodeInfo(newId, part, psiPackage))
          }
        }
        node.userObject.counter.append(clazz.counter)
        node.getOrCreateChild(clazz)
      }
    }

    collapseLongEdges(root)

    nodeMap.clear()
    TreeUtil.treeNodeTraverser(root).forEach {
      val node = it as CoverageTreeNode
      nodeMap[node.userObject.id] = node
    }
  }

  private fun collapseLongEdges(root: CoverageTreeNode) {
    val nodes = mutableListOf<CoverageTreeNode>()
    nodes.add(root)
    while (nodes.isNotEmpty()) {
      val node = nodes.removeLast()
      for (child in node.children()) {
        nodes.add(child as CoverageTreeNode)
      }

      collapseNode(node)
    }
  }

  private fun collapseNode(node: CoverageTreeNode) {
    val parent = node.parent as CoverageTreeNode? ?: return
    if (node.childCount != 1) return
    val child = node.getChildAt(0) as CoverageTreeNode
    if (child.isLeaf) return

    parent.remove(node)
    parent.add(child)
    child.userObject = child.userObject.let { it.copy(name = "${node.userObject.name}.${it.name}") }
  }

  private fun CoverageTreeNode.getOrCreateChild(info: CoverageNodeInfo): CoverageTreeNode {
    for (child in children()) {
      val childInfo = (child as CoverageTreeNode).userObject
      if (childInfo.id == info.id) return child
    }
    return CoverageTreeNode(info).also { add(it) }
  }

  private fun isModified(className: String): Boolean = runReadAction {
    val psiClass = getPsiClass(className)?.takeIf { it.isValid } ?: return@runReadAction false
    val virtualFile = psiClass.containingFile.virtualFile
    val status = fileStatusManager.getStatus(virtualFile)
    return@runReadAction CoverageViewExtension.isModified(status)
  }

  private fun getPsiClass(className: String): PsiNamedElement? = cache.getOrPut(className) {
    DumbService.getInstance(project).runReadActionInSmartMode(Computable { JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.projectScope(project)) })
  }

  private fun getPsiPackage(packageName: String): PsiNamedElement? = cache.getOrPut(packageName) {
    DumbService.getInstance(project).runReadActionInSmartMode(Computable { JavaPsiFacade.getInstance(project).findPackage(packageName) })
  }

  override fun dispose() {
  }
}

class TypedTreeNode<E>(userObject: E) : DefaultMutableTreeNode(userObject) {
  override fun getUserObject(): E {
    return super.getUserObject() as E
  }
}

typealias CoverageTreeNode = TypedTreeNode<CoverageNodeInfo>
