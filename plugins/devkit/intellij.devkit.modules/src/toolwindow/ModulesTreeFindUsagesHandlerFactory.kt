// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesHandlerBase
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import org.jetbrains.jps.model.module.JpsModule

/**
 * Factory for creating FindUsagesHandler for module tree nodes.
 */
class ModulesTreeFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    /**
     * Checks if this factory can find usages of the specified element.
     */
    override fun canFindUsages(element: PsiElement): Boolean {
        // Check if the element is a module tree node
        return element.getUserData(ModulesTreeReferenceProvider.MODULE_NODE_KEY) != null
    }

    /**
     * Creates a FindUsagesHandler for the specified element.
     */
    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
        // Get the module node from the element
        val moduleNode = element.getUserData(ModulesTreeReferenceProvider.MODULE_NODE_KEY) ?: return null

        // Create a handler based on the type of node
        return when (moduleNode) {
            is ModulesTreeModelService.SourceRootNode -> ModulesTreeFindUsagesHandler(element, moduleNode)
            is ModulesTreeModelService.ResourceRootNode -> ModulesTreeFindUsagesHandler(element, moduleNode)
            is ModulesTreeModelService.TestRootNode -> ModulesTreeFindUsagesHandler(element, moduleNode)
            is ModulesTreeModelService.TestResourceRootNode -> ModulesTreeFindUsagesHandler(element, moduleNode)
            is ModulesTreeModelService.ModuleDependencyNode -> ModulesTreeFindUsagesHandler(element, moduleNode)
            is ModulesTreeModelService.LibraryDependencyNode -> ModulesTreeFindUsagesHandler(element, moduleNode)
            is ModulesTreeModelService.PluginXmlFileWithInfo -> ModulesTreeFindUsagesHandler(element, moduleNode)
            is ModulesTreeModelService.ModuleXmlFileWithInfo -> ModulesTreeFindUsagesHandler(element, moduleNode)
            is ModulesTreeModelService.ContentModuleNode -> ModulesTreeFindUsagesHandler(element, moduleNode)
            is ModulesTreeModelService.DependencyPluginNode -> ModulesTreeFindUsagesHandler(element, moduleNode)
            is ModulesTreeModelService.OldFashionDependencyNode -> ModulesTreeFindUsagesHandler(element, moduleNode)
            is ModulesTreeModelService.ModuleValueNode -> ModulesTreeFindUsagesHandler(element, moduleNode)
            is JpsModule -> ModulesTreeFindUsagesHandler(element, moduleNode)
            else -> null
        }
    }
}

/**
 * Handler for finding usages of module tree nodes.
 */
class ModulesTreeFindUsagesHandler(
    element: PsiElement,
    private val moduleNode: Any
) : FindUsagesHandler(element) {
    /**
     * Finds references to highlight for the specified target element.
     */
    override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope): Collection<PsiReference> {
        // Get the references from the reference provider
        return ModulesTreeReferenceProvider.getInstance(target.project).findReferences(moduleNode, searchScope)
    }
}
