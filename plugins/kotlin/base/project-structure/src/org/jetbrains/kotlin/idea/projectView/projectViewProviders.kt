// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.projectView

import com.intellij.ide.projectView.SelectableTreeStructureProvider
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.FileNodeWithNestedFileNodes
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.idea.base.util.KotlinSingleClassFileAnalyzer
import org.jetbrains.kotlin.idea.util.isFileInRoots
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

class KotlinExpandNodeProjectViewProvider : TreeStructureProvider, DumbAware {

    // should be called after ClassesTreeStructureProvider
    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>>,
        settings: ViewSettings
    ): Collection<AbstractTreeNode<out Any>> {
        val result = ArrayList<AbstractTreeNode<out Any>>()

        for (child in children) {
            val value = child.value
            val ktFile = value?.asKtFile()
            val nestedFileNodes = (child as? FileNodeWithNestedFileNodes)?.nestedFileNodes ?: emptyList()

            if (ktFile != null) {
                val mainClass = KotlinSingleClassFileAnalyzer.getSingleClass(ktFile)
                if (mainClass != null && mainClass.containingKtFile.declarations.size == 1) {
                    // Only use a KtClassOrObjectTreeNode if the file contains only the class.
                    // Otherwise, the move behavior when trying to move the node will only move the mainClass,
                    // which is unexpected.
                    result.add(KtClassOrObjectTreeNode(ktFile.project, mainClass, settings, nestedFileNodes))
                } else {
                    result.add(KtFileTreeNode(ktFile.project, ktFile, settings, nestedFileNodes))
                }
            } else {
                if (value is KtLightClass) {
                    result.add(KtInternalFileTreeNode(value.project, value, settings, nestedFileNodes))
                } else {
                    result.add(child)
                }
            }

        }

        return result
    }

    private fun Any.asKtFile(): KtFile? = when (this) {
        is KtFile -> this
        is KtLightClassForFacade -> files.singleOrNull()
        is KtLightClass -> kotlinOrigin?.containingFile as? KtFile
        else -> null
    }
}


class KotlinSelectInProjectViewProvider(private val project: Project) : SelectableTreeStructureProvider, DumbAware {

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>>,
        settings: ViewSettings
    ): Collection<AbstractTreeNode<out Any>> {
        return ArrayList(children)
    }


    // should be called before ClassesTreeStructureProvider
    override fun getTopLevelElement(element: PsiElement): PsiElement? {
        if (!element.isValid) return null
        val file = element.containingFile as? KtFile ?: return null

        val virtualFile = file.virtualFile
        if (!project.isFileInRoots(virtualFile)) return file

        var current = element.parentsWithSelf.firstOrNull { it.isSelectable() }

        if (current is KtFile) {
            val declaration = current.declarations.singleOrNull()
            val nameWithoutExtension = virtualFile?.nameWithoutExtension ?: file.name
            if (declaration is KtClassOrObject && nameWithoutExtension == declaration.name) {
                current = declaration
            }
        }

        return current ?: file
    }

    private fun PsiElement.isSelectable(): Boolean = when (this) {
        is KtFile -> true
        is KtDeclaration -> parent is KtFile || ((parent as? KtClassBody)?.parent as? KtClassOrObject)?.isSelectable() ?: false
        else -> false
    }
}

@ApiStatus.Internal
fun KtClassOrObject.getStructureDeclarations(): List<KtDeclaration> =
    buildList {
        primaryConstructor?.let { add(it) }
        primaryConstructorParameters.filterTo(this) { it.hasValOrVar() }
        addAll(declarations)
    }