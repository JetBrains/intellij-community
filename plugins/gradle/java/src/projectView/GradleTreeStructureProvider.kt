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
import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
import com.intellij.util.SmartList
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleTreeStructureProvider : TreeStructureProvider, DumbAware {

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>>,
        settings: ViewSettings
    ): Collection<AbstractTreeNode<*>> {
        val project = parent.project ?: return children

        return when (parent) {
            is ProjectViewProjectNode -> getProjectNodeChildren(project, children)
            is ProjectViewModuleGroupNode -> {
                val modifiedChildren = SmartList<AbstractTreeNode<*>>()
                for (child in children) {
                    val newChild = when (child) {
                        is ProjectViewModuleNode -> {
                            val module = child.value
                            if (showUnderModuleGroup(module)) {
                                getGradleModuleNode(project, child, settings) ?: child
                            } else {
                                continue
                            }
                        }
                        is PsiDirectoryNode -> {
                            val sourceSetNode = getGradleModuleNode(project, child, settings)
                            if (sourceSetNode != null && !showUnderModuleGroup(sourceSetNode.myModule)) {
                                continue
                            }
                            sourceSetNode ?: child
                        }
                        else -> child
                    }
                    modifiedChildren.add(newChild)
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
                        if (projectPath != null && virtualFile != null &&
                            FileUtil.isAncestor(projectPath, virtualFile.path, false)
                        ) {
                            continue
                        }
                    }
                    modifiedChildren.add(child)
                }
                modifiedChildren
            }
            is PsiDirectoryNode -> {
                val modifiedChildren = SmartList<AbstractTreeNode<*>>()
                for (child in children) {
                    val newChild = if (child is PsiDirectoryNode) {
                        getGradleModuleNode(project, child, settings) ?: child
                    } else {
                        child
                    }
                    modifiedChildren.add(newChild)
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

    private fun getProjectNodeChildren(
        project: Project,
        children: Collection<AbstractTreeNode<*>>
    ): Collection<AbstractTreeNode<*>> {
        val modifiedChildren = SmartList<AbstractTreeNode<*>>()
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        for (child in children) {
            var parentNodePair: Pair<VirtualFile, PsiDirectoryNode>? = null
            when (child) {
                is ProjectViewModuleGroupNode -> {
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
                        } else {
                            parentNodePair = null
                            break
                        }
                    }
                }
                is PsiDirectoryNode -> {
                    val psiDirectory = child.value
                    val directoryFile = psiDirectory?.virtualFile
                    val gradleModuleNode = getGradleModuleNode(
                        project,
                        child,
                        child.settings
                    )
                    if (gradleModuleNode != null) {
                        parentNodePair = Pair(directoryFile!!, gradleModuleNode)
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
        settings: ViewSettings
    ): GradleProjectViewModuleNode? {
        val module = moduleNode.value ?: return null
        val moduleShortName = getGradleModuleShortName(module) ?: return null
        return GradleProjectViewModuleNode(project, module, settings, moduleShortName)
    }

    private fun getGradleModuleNode(
        project: Project,
        directoryNode: PsiDirectoryNode,
        settings: ViewSettings
    ): GradleModuleDirectoryNode? {
        val psiDirectory = directoryNode.value ?: return null
        val virtualFile = psiDirectory.virtualFile
        if (!ProjectRootsUtil.isModuleContentRoot(virtualFile, project)) return null

        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val module = fileIndex.getModuleForFile(virtualFile) ?: return null
        val moduleShortName = getGradleModuleShortName(module) ?: return null
        return GradleModuleDirectoryNode(
            project,
            psiDirectory,
            settings,
            module,
            moduleShortName,
            directoryNode.filter
        )
    }

    private fun getGradleModuleShortName(module: Module): String? {
        if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return null
        val moduleShortName = when {
            isSourceSetModule(module) -> GradleProjectResolverUtil.getSourceSetName(module)
            else -> ExternalSystemApiUtil.getExternalProjectId(module)
        }
        val isRootModule = ExternalSystemApiUtil.getExternalProjectPath(module) == ExternalSystemApiUtil.getExternalRootProjectPath(module)
        return if (isRootModule || moduleShortName == null) {
            moduleShortName
        } else {
            val shortenedName = ModuleGrouper.instanceFor(module.project).getShortenedNameByFullModuleName(moduleShortName)
            shortenedName.substringAfterLast(':')
        }
    }

    private fun isSourceSetModule(module: Module): Boolean {
        return GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY == ExternalSystemApiUtil.getExternalModuleType(module)
    }

    inner class GradleModuleDirectoryNode(
      project: Project,
      psiDirectory: PsiDirectory,
      settings: ViewSettings,
      val myModule: Module,
      private val myModuleShortName: String,
      filter: PsiFileSystemItemFilter?
    ) : PsiDirectoryNode(project, psiDirectory, settings, filter) {

        private val appendModuleName: Boolean
        private val isSourceSetModule: Boolean

        init {
            val directoryFile = psiDirectory.virtualFile
            appendModuleName = myModuleShortName.isNotEmpty() &&
                myModuleShortName.replace("-", "", true) != directoryFile.name.replace("-", "", true)
            isSourceSetModule = isSourceSetModule(myModule)
        }

        override fun shouldShowModuleName(): Boolean {
            return !(appendModuleName || isSourceSetModule) || canRealModuleNameBeHidden()
        }

        override fun updateImpl(data: PresentationData) {
            super.updateImpl(data)
            when {
                appendModuleName && !canRealModuleNameBeHidden() -> data.addText("[$myModuleShortName]", REGULAR_BOLD_ATTRIBUTES)
                isSourceSetModule -> {
                    val fragments = data.coloredText
                    if (fragments.size == 1) {
                        val fragment = fragments.first()
                        data.clearText()
                        data.addText(fragment.text.trim(), SimpleTextAttributes.merge(fragment.attributes, REGULAR_BOLD_ATTRIBUTES))
                    }
                }
            }
        }
    }

    private class GradleProjectViewModuleNode(
        project: Project,
        value: Module,
        viewSettings: ViewSettings,
        private val myModuleShortName: String
    ) : ProjectViewModuleNode(project, value, viewSettings) {

        override fun update(presentation: PresentationData) {
            super.update(presentation)
            presentation.presentableText = myModuleShortName
            presentation.addText(myModuleShortName, REGULAR_BOLD_ATTRIBUTES)
        }

        override fun showModuleNameInBold() = false
    }
}