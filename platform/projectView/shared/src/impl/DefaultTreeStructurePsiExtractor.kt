// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.ide.projectView.impl.AbstractProjectViewPane.extractDirectories
import com.intellij.ide.projectView.impl.AbstractProjectViewPane.extractPsiElementsFromNodeOrUserObject
import com.intellij.ide.projectView.impl.AbstractProjectViewPane.extractValueFromNode
import com.intellij.ide.projectView.impl.ModuleGroup
import com.intellij.ide.projectView.impl.getNodeElement
import com.intellij.ide.projectView.impl.moduleContext
import com.intellij.ide.projectView.impl.moduleContexts
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement
import com.intellij.ide.projectView.impl.unloadedModules
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.UnloadedModuleDescription
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class DefaultTreeStructurePsiExtractor(private val project: Project) : ProjectViewPsiExtractor<TreeStructureProjectViewNode> {

  override fun extractPsiElements(nodes: List<BackendProjectViewNodeModel<TreeStructureProjectViewNode>>): List<PsiElement> {
    return nodes.flatMap { extractPsiElements(it) }
  }

  protected open fun extractPsiElements(node: BackendProjectViewNodeModel<TreeStructureProjectViewNode>): List<PsiElement> {
    val userObject = node.legacyUserObject
    val value = extractValueFromNode(userObject)
    return extractPsiElementsFromNodeOrUserObject(project, userObject, value)
  }

  override fun extractPsiDirectories(nodes: List<BackendProjectViewNodeModel<TreeStructureProjectViewNode>>): List<PsiDirectory> {
    return extractDirectories(
      project,
      nodes.map { it.legacyUserObject as Any }.toTypedArray(),
    ) { node ->
      val value = extractValueFromNode(node)
      extractPsiElementsFromNodeOrUserObject(project, node, value)
    }.toList()
  }

  override fun extractProject(node: BackendProjectViewNodeModel<TreeStructureProjectViewNode>): Project? {
    return getNodeElement(node.legacyUserObject) as? Project?
  }

  override fun extractSingleModule(node: BackendProjectViewNodeModel<TreeStructureProjectViewNode>): Module? {
    return getNodeElement(node.legacyUserObject)?.let { moduleContext(project, it) }
  }

  override fun extractModules(nodes: List<BackendProjectViewNodeModel<TreeStructureProjectViewNode>>): List<Module> {
    return nodes.flatMap { extractModules(it) }
  }

  private fun extractModules(node: BackendProjectViewNodeModel<TreeStructureProjectViewNode>): List<Module> {
    return getNodeElement(node.legacyUserObject)?.let { moduleContexts(project, arrayOf(it)) } ?: emptyList()
  }

  override fun extractUnloadedModules(nodes: List<BackendProjectViewNodeModel<TreeStructureProjectViewNode>>): List<UnloadedModuleDescription> {
    return nodes.flatMap { extractUnloadedModules(it) }
  }

  private fun extractUnloadedModules(node: BackendProjectViewNodeModel<TreeStructureProjectViewNode>): List<UnloadedModuleDescription> {
    return getNodeElement(node.legacyUserObject)?.let { unloadedModules(project, arrayOf(it)) } ?: emptyList()
  }

  override fun extractModuleGroups(nodes: List<BackendProjectViewNodeModel<TreeStructureProjectViewNode>>): List<ModuleGroup> {
    return extractValuesFromNodes(nodes).filterIsInstance<ModuleGroup>()
  }

  override fun extractLibraryGroups(nodes: List<BackendProjectViewNodeModel<TreeStructureProjectViewNode>>): List<LibraryGroupElement> {
    return extractValuesFromNodes(nodes).filterIsInstance<LibraryGroupElement>()
  }

  override fun extractNamedLibraryElements(nodes: List<BackendProjectViewNodeModel<TreeStructureProjectViewNode>>): List<NamedLibraryElement> {
    return extractValuesFromNodes(nodes).filterIsInstance<NamedLibraryElement>()
  }

  private fun extractValuesFromNodes(nodes: List<BackendProjectViewNodeModel<TreeStructureProjectViewNode>>): List<Any> {
    return nodes.flatMap { extractValuesFromUserObject(it.legacyUserObject) }
  }

  private fun extractValuesFromUserObject(userObject: Any): List<Any> {
    val value = extractValueFromNode(userObject)
    if (value is Array<*>) {
      return value.filterNotNull()
    }
    else {
      return listOfNotNull(value)
    }
  }
}

private val BackendProjectViewNodeModel<TreeStructureProjectViewNode>.legacyUserObject: NodeDescriptor<*>
  get() = userObject.elementDescriptor
