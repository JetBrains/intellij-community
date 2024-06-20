// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.java.JavaBundle
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.SearchScope
import com.intellij.util.ArrayUtilRt
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

open class KotlinSubtypesHierarchyTreeStructure : HierarchyTreeStructure {
    private val myCurrentScopeType: String

    protected constructor(project: Project, descriptor: HierarchyNodeDescriptor, currentScopeType: String) : super(project, descriptor) {
        myCurrentScopeType = currentScopeType
    }

    constructor(project: Project, klass: KtClassOrObject, currentScopeType: String) : super(
        project, KotlinTypeHierarchyNodeDescriptor(
            project, null, klass, true
        )
    ) {
        myCurrentScopeType = currentScopeType
    }

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val element = descriptor.psiElement as? PsiNamedElement ?: return ArrayUtilRt.EMPTY_OBJECT_ARRAY

        if (StandardClassIds.Any.asSingleFqName() == (element as? KtClass)?.fqName ||
            CommonClassNames.JAVA_LANG_OBJECT == (element as? PsiClass)?.qualifiedName
        ) {
            return arrayOf(JavaBundle.message("node.hierarchy.java.lang.object"))
        }

        if (element.name == null) return ArrayUtilRt.EMPTY_OBJECT_ARRAY

        val searchScope = element.getUseScope().intersectWith(getSearchScope(myCurrentScopeType, element))
        val directInheritors = searchInheritors(element, searchScope)
        val descriptors = mutableListOf<HierarchyNodeDescriptor>()
        for (inheritor in directInheritors) {
            descriptors.add(KotlinTypeHierarchyNodeDescriptor.createTypeHierarchyDescriptor(inheritor, descriptor))
        }
        return descriptors.toTypedArray()
    }

    companion object {
        private fun searchInheritors(klass: PsiElement, searchScope: SearchScope): Sequence<PsiElement> {
            //todo annotations
            //todo functional interfaces
            return KotlinFindUsagesSupport.searchInheritors(klass, searchScope, searchDeeply = false)
        }
    }
}
