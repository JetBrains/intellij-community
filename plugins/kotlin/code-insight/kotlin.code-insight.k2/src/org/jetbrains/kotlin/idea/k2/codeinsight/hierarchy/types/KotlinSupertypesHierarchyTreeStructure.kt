// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.type.SupertypesHierarchyTreeStructure
import com.intellij.ide.hierarchy.type.TypeHierarchyNodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFunctionalExpression
import com.intellij.util.ArrayUtilRt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getSuperNames
import org.jetbrains.kotlin.utils.addIfNotNull

class KotlinSupertypesHierarchyTreeStructure(project: Project, aClass: KtClassOrObject) :
    HierarchyTreeStructure(project, KotlinTypeHierarchyNodeDescriptor(project, null, aClass, true)) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any?> {
        val element = descriptor.psiElement
        when (element) {
            is KtClassOrObject -> {
                val supers = getSupers(element)

                val descriptors = mutableListOf<HierarchyNodeDescriptor>()

                for (aSuper in supers) {
                    descriptors.add(KotlinTypeHierarchyNodeDescriptor.createTypeHierarchyDescriptor(aSuper, descriptor))
                }
                return descriptors.toTypedArray()
            }

            is PsiClass -> {
                val supers = SupertypesHierarchyTreeStructure.getSupers(element)

                val descriptors = mutableListOf<HierarchyNodeDescriptor>()
                for (aSuper in supers) {
                    descriptors.add(KotlinTypeHierarchyNodeDescriptor.createTypeHierarchyDescriptor(aSuper, descriptor))
                }
                return descriptors.toTypedArray()
            }

            is PsiFunctionalExpression -> {
                val functionalInterfaceClass = LambdaUtil.resolveFunctionalInterfaceClass(element)
                if (functionalInterfaceClass != null) {
                    return arrayOf(TypeHierarchyNodeDescriptor(myProject, descriptor, functionalInterfaceClass, false))
                }
            }
        }
        return ArrayUtilRt.EMPTY_OBJECT_ARRAY
    }

    companion object {
        private fun getSupers(klass: KtClassOrObject): Array<PsiElement> {
            return analyze(klass) {
                val klassSymbol = klass.symbol as? KaClassSymbol ?: return PsiElement.EMPTY_ARRAY

                val isInterface = klass is KtClass && klass.isInterface()
                if (isInterface && klass.getSuperNames().isEmpty()) {
                    //don't add Any for interface super list
                    return PsiElement.EMPTY_ARRAY
                }

                if (klass is KtClass && klass.isAnnotation()) {
                    return klass.symbol.annotations.mapNotNull { it.constructorSymbol?.containingSymbol?.psi }.toTypedArray()
                }

                val elements = klassSymbol.superTypes.mapNotNull { it.symbol?.psi }
                if (elements.isNotEmpty() && !isInterface && elements.all { it is KtClass && it.isInterface() || it is PsiClass && it.isInterface }) {
                    val superClasses = mutableListOf<PsiElement>()
                    superClasses.addIfNotNull(builtinTypes.any.symbol?.psi)
                    superClasses.addAll(elements)
                    superClasses.toTypedArray()
                } else elements.toTypedArray()
            }
        }
    }
}