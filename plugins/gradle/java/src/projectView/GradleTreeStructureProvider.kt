// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.projectView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleGroupNode
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
import com.intellij.util.SmartList
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleTreeStructureProvider : TreeStructureProvider, DumbAware {

  override fun modify(
    parent: AbstractTreeNode<*>,
    children: Collection<AbstractTreeNode<*>>,
    settings: ViewSettings,
  ): Collection<AbstractTreeNode<*>> {
    val project = parent.project ?: return children

    return when (parent) {
      is ProjectViewProjectNode -> getProjectNodeChildren(project, children)
      is ProjectViewModuleGroupNode -> {
        val modifiedChildren = SmartList<AbstractTreeNode<*>>()
        for (child in children) {
          if (child is ProjectViewModuleNode) {
            val module = child.value
            if (!showUnderModuleGroup(module)) continue
            modifiedChildren.add(getGradleModuleNode(project, child, settings) ?: child)
            continue
          }
          if (child is PsiDirectoryNode) {
            val psiDirectory = child.value
            if (psiDirectory != null) {
              val virtualFile = psiDirectory.virtualFile
              if (ProjectRootsUtil.isModuleContentRoot(virtualFile, project)) {
                val fileIndex = ProjectRootManager.getInstance(project).fileIndex
                val module = fileIndex.getModuleForFile(virtualFile)
                if (module != null && !showUnderModuleGroup(module)) continue
              }
            }
          }
          modifiedChildren.add(child)
        }
        modifiedChildren
      }
      is GradleProjectViewModuleNode -> {
        val module = parent.value
        val projectPath = ExternalSystemApiUtil.getExternalProjectPath(module)
        val modifiedChildren = SmartList<AbstractTreeNode<*>>()
        for (child in children) {
          if (child is PsiDirectoryNode) {
            val psiDirectory = child.value
            val virtualFile = psiDirectory?.virtualFile
            if (projectPath != null && virtualFile != null && FileUtil.isAncestor(projectPath, virtualFile.path, false)) {
              continue
            }
          }
          modifiedChildren.add(child)
        }
        modifiedChildren
      }
      else -> children
    }
  }

  private fun showUnderModuleGroup(module: Module): Boolean {
    if (ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
      val projectPath = ExternalSystemApiUtil.getExternalProjectPath(module)
      for (root in ModuleRootManager.getInstance(module).contentRoots) {
        if (projectPath != null && !FileUtil.isAncestor(projectPath, root.path, true)) {
          return true
        }
      }
      return false
    }
    return true
  }

  private fun getProjectNodeChildren(project: Project, children: Collection<AbstractTreeNode<*>>): Collection<AbstractTreeNode<*>> {
    val modifiedChildren = SmartList<AbstractTreeNode<*>>()
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    for (child in children) {
      var parentNodePair: Pair<VirtualFile, PsiDirectoryNode>? = null
      if (child is ProjectViewModuleGroupNode) {
        for (node in child.children) {
          if (node is PsiDirectoryNode) {
            val psiDirectory = node.value ?: run {
              parentNodePair = null
              break
            }
            val virtualFile = psiDirectory.virtualFile
            val module = fileIndex.getModuleForFile(virtualFile)
            if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
              parentNodePair = null
              break
            }
            parentNodePair = when {
                               parentNodePair == null -> Pair(virtualFile, node)
                               VfsUtilCore.isAncestor(virtualFile, parentNodePair.first, false) -> parentNodePair
                               !VfsUtilCore.isAncestor(parentNodePair.first, virtualFile, false) -> null
                               else -> parentNodePair
                             } ?: break
          }
          else {
            parentNodePair = null
            break
          }
        }
      }
      modifiedChildren.add(parentNodePair?.second ?: child)
    }
    return modifiedChildren
  }

  private fun getGradleModuleNode(
    project: Project,
    moduleNode: ProjectViewModuleNode,
    settings: ViewSettings,
  ): GradleProjectViewModuleNode? {
    val module = moduleNode.value ?: return null
    val moduleShortName = getGradleModuleShortName(module) ?: return null
    return GradleProjectViewModuleNode(project, module, settings, moduleShortName)
  }

  private class GradleProjectViewModuleNode(
    project: Project,
    value: Module,
    viewSettings: ViewSettings,
    private val myModuleShortName: @NlsSafe String,
  ) : ProjectViewModuleNode(project, value, viewSettings) {

    override fun update(presentation: PresentationData) {
      super.update(presentation)
      presentation.presentableText = myModuleShortName
      presentation.addText(myModuleShortName, REGULAR_BOLD_ATTRIBUTES)
    }

    override fun showModuleNameInBold() = false
  }
}