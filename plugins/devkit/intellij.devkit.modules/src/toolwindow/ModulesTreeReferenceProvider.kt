// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.openapi.util.KeyWithDefaultValue

/**
 * Provider for finding references to module tree nodes.
 */
@Service(Service.Level.PROJECT)
class ModulesTreeReferenceProvider(private val project: Project) {
    companion object {
        /**
         * Key for storing module node in user data of PsiElement.
         */
        val MODULE_NODE_KEY = KeyWithDefaultValue.create<Any>("MODULE_NODE_KEY", null as Any?)

        /**
         * Gets the ModulesTreeReferenceProvider instance for the specified project.
         */
        @JvmStatic
        fun getInstance(project: Project): ModulesTreeReferenceProvider {
            return project.service<ModulesTreeReferenceProvider>()
        }
    }

    /**
     * Finds references to the specified module node.
     */
    fun findReferences(moduleNode: Any, searchScope: SearchScope): Collection<PsiReference> {
        // For now, return an empty list
        // In a real implementation, this would search for references to the module node
        return emptyList()
    }

    /**
     * Associates a PsiElement with a module node.
     */
    fun associateElementWithNode(element: PsiElement, moduleNode: Any) {
        element.putUserData(MODULE_NODE_KEY, moduleNode)
    }
}
