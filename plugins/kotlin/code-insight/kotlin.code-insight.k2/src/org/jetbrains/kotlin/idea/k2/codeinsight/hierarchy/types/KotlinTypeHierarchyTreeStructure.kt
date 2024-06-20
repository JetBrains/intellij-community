// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types.KotlinTypeHierarchyNodeDescriptor.Companion.createTypeHierarchyDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

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
            if (!aClass.isValid()) return emptyArray()
            if ((aClass as? KtClass)?.isInterface() == true) return emptyArray()
            val className = aClass.name ?: return emptyArray()

            val superClasses = mutableSetOf<PsiElement>()
            analyzeInModalWindow(aClass,
                                 KotlinBundle.message("dialog.title.build.super.types.hierarchy", className)
            ) {
                var currentClass: PsiElement? = aClass
                while (currentClass != null) {
                    val superClass = when (currentClass) {
                        is KtClassOrObject -> {

                            ((currentClass as KtClassOrObject).symbol as? KaClassSymbol)?.superTypes?.firstOrNull { superType ->
                                val psi = superType.symbol?.psi ?: return@firstOrNull false
                                when (psi) {
                                    is KtClass -> !psi.isInterface()
                                    is PsiClass -> !psi.isInterface
                                    else -> false
                                }
                            }?.symbol?.psi

                        }

                        is PsiClass -> {
                            currentClass.superClass
                        }

                        else -> null
                    }
                    if (superClass == null) {
                        break
                    }

                    if (!superClasses.add(superClass)) {
                        break
                    }
                    currentClass = superClass
                }
            }

            return superClasses.toTypedArray()
        }
    }
}
