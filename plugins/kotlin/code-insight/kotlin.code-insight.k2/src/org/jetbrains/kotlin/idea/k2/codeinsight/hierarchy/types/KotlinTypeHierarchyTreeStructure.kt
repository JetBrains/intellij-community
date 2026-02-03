// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types.KotlinTypeHierarchyNodeDescriptor.Companion.createTypeHierarchyDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.utils.addIfNotNull

class KotlinTypeHierarchyTreeStructure(project: Project, aClass: KtClassOrObject, currentScopeType: String) :
    KotlinSubtypesHierarchyTreeStructure(project, buildHierarchyElement(project, aClass), currentScopeType) {
    init {
        setBaseElement(myBaseDescriptor) //to set myRoot
    }

    override fun toString(): String {
        return "Type Hierarchy for " + formatBaseElementText()
    }

    companion object {

        private fun buildHierarchyElement(project: Project, aClass: KtClassOrObject): HierarchyNodeDescriptor {
            var descriptor: HierarchyNodeDescriptor? = null
            val superClasses = createSuperClasses(aClass)
            for (superClass in superClasses.reversed()) {
                val newDescriptor =
                    createTypeHierarchyDescriptor(superClass, descriptor)
                descriptor?.cachedChildren = arrayOf(newDescriptor)
                descriptor = newDescriptor
            }

            val newDescriptor = KotlinTypeHierarchyNodeDescriptor(project, descriptor, aClass, true)
            descriptor?.cachedChildren = arrayOf(newDescriptor)
            return newDescriptor
        }

        private fun createSuperClasses(aClass: KtClassOrObject): Array<PsiElement> {
            if (!aClass.isValid()) return PsiElement.EMPTY_ARRAY
            if ((aClass as? KtClass)?.isInterface() == true) return PsiElement.EMPTY_ARRAY
            val className = aClass.name ?: return PsiElement.EMPTY_ARRAY

            return analyzeInModalWindow(
                aClass, KotlinBundle.message("dialog.title.build.super.types.hierarchy", className)
            ) {
                val superClasses = mutableSetOf<PsiElement>()
                var currentClassSymbol: KaClassSymbol? = aClass.namedClassSymbol
                val superSymbols = mutableSetOf<KaClassSymbol>()
                while (currentClassSymbol != null) {
                    currentClassSymbol = currentClassSymbol.superTypes.map { it.symbol }.filterIsInstance<KaClassSymbol>()
                        .firstOrNull { it.classKind != KaClassKind.INTERFACE }
                    if (currentClassSymbol != null) {
                        if (!superSymbols.add(currentClassSymbol)) {
                            break
                        }
                        superClasses.addIfNotNull(currentClassSymbol.psi)
                    }
                }
                superClasses.toTypedArray()
            }
        }
    }
}
