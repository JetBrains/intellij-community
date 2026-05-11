// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.java.JavaBundle
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import com.intellij.util.ArrayUtilRt
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.util.excludeKotlinSources
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpect
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.expectDeclarationIfAny
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isActualDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration

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

        val baseScope = getSearchScope(myCurrentScopeType, element)
        val searchScope = element.getUseScope().intersectWith(baseScope)
        val directInheritors = searchInheritors(element, searchScope, baseScope)
        return directInheritors
            .map { KotlinTypeHierarchyNodeDescriptor.createTypeHierarchyDescriptor(it, descriptor) }
            .toTypedArray()
    }

    companion object {
        private fun searchInheritors(klass: PsiElement, searchScope: SearchScope, baseScope: SearchScope): List<PsiElement> {
            val psiClass = when (klass) {
                is KtClass -> klass.toLightClass()
                is PsiClass -> klass
                else -> null
            }

            if (klass is KtClass && klass.isAnnotation()) {
                val javaAnnotations = if (psiClass != null) {
                    AnnotatedElementsSearch.searchPsiClasses(psiClass, searchScope.excludeKotlinSources(klass.project))
                        .asIterable()
                        .filter { it.isAnnotationType }
                } else emptyList()

                val candidates =  when (searchScope) {
                      is GlobalSearchScope -> {
                          val name = klass.name
                          val scope = KotlinSourceFilterScope.everything(searchScope, klass.project)
                          name?.let { KotlinAnnotationsIndex[name, klass.project, scope] } ?: emptyList()
                      }

                        else -> (searchScope as LocalSearchScope).scope.flatMap { it.collectDescendantsOfType<KtAnnotationEntry>() }
                    }
                return candidates.mapNotNull { entry ->
                    entry.getStrictParentOfType<KtClass>()?.takeIf {
                            it.isAnnotation() && it.annotationEntries.contains(entry) && entry.calleeExpression?.constructorReferenceExpression?.mainReference?.resolve() == klass
                        }
                } + javaAnnotations
            }

            if (klass is PsiClass && klass.isAnnotationType) {
                return AnnotatedElementsSearch.searchPsiClasses(klass, searchScope).asIterable().filter { it.isAnnotationType }
            }

            val inheritors = if (klass is KtClassOrObject) {
                val isExpectDeclaration = klass.isExpectDeclaration()
                val isActualDeclaration = klass.isActualDeclaration()
                val expectedClassOrObject = klass.expectDeclarationIfAny() as? KtClassOrObject ?: klass
                val actuals = if (isExpectDeclaration) expectedClassOrObject.actualsForExpect() else emptyList()
                val withCommonScope =
                    if (klass == expectedClassOrObject) {
                        searchScope
                    } else {
                        val expectClassScope = expectedClassOrObject.useScope.intersectWith(klass.resolveScope)
                        searchScope.union(expectClassScope.intersectWith(baseScope))
                    }
                actuals + KotlinFindUsagesSupport.searchInheritors(expectedClassOrObject, withCommonScope, searchDeeply = false)
                            .filter { inheritor ->
                                if (isExpectDeclaration) {
                                    // skip inheritors of the platform classes, they are included in the next level of `actuals` list
                                    val scope = inheritor.module?.moduleWithDependenciesScope
                                    scope != null && actuals.none {
                                        PsiSearchScopeUtil.isInScope(scope, it)
                                    }
                                } else if (isActualDeclaration) {
                                    // do not include expect declarations, they should be actualized in platform
                                    (inheritor as? KtDeclaration)?.isExpectDeclaration() != true
                                }
                                else {
                                    // started from `normal`;
                                    true
                                }
                            }.toList()
            } else {
                KotlinFindUsagesSupport.searchInheritors(klass, searchScope, searchDeeply = false).toList()
            }

            if (psiClass == null || !LambdaUtil.isFunctionalClass(psiClass)) {
                return inheritors
            }

            return inheritors + FunctionalExpressionSearch.search(psiClass, searchScope).asIterable()
        }
    }
}
