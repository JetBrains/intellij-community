// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.util.Processor
import org.jetbrains.kotlin.asJava.toLightClassWithBuiltinMapping
import org.jetbrains.kotlin.asJava.classes.KtFakeLightClass
import org.jetbrains.kotlin.asJava.toFakeLightClass
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.idea.stubindex.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.stubindex.KotlinSuperClassIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasByExpansionShortNameIndex

open class KotlinDirectInheritorsSearcher : QueryExecutorBase<PsiClass, DirectClassInheritorsSearch.SearchParameters>(true) {
    override fun processQuery(queryParameters: DirectClassInheritorsSearch.SearchParameters, consumer: Processor<in PsiClass>) {
        val baseClass = queryParameters.classToProcess

        val baseClassName = baseClass.name ?: return

        val file = if (baseClass is KtFakeLightClass) baseClass.kotlinOrigin.containingFile else baseClass.containingFile

        val originalScope = queryParameters.scope
        val scope = originalScope as? GlobalSearchScope ?: file.fileScope()

        val names = mutableSetOf(baseClassName)
        val project = file.project

        val typeAliasIndex = KotlinTypeAliasByExpansionShortNameIndex.getInstance()

        fun searchForTypeAliasesRecursively(typeName: String) {
            ProgressManager.checkCanceled()
            typeAliasIndex[typeName, project, scope].asSequence()
                .map { it.name }
                .filterNotNull()
                .filter { it !in names }
                .onEach { names.add(it) }
                .forEach(::searchForTypeAliasesRecursively)
        }

        searchForTypeAliasesRecursively(baseClassName)

        val noLibrarySourceScope = KotlinSourceFilterScope.projectSourceAndClassFiles(scope, project)
        names.forEach { name ->
            ProgressManager.checkCanceled()
            KotlinSuperClassIndex.getInstance()
                .get(name, project, noLibrarySourceScope).asSequence()
                .mapNotNull { candidate ->
                    ProgressManager.checkCanceled()
                    candidate.toLightClassWithBuiltinMapping() ?: candidate.toFakeLightClass()
                }
                .filter { candidate ->
                    ProgressManager.checkCanceled()
                    candidate.isInheritor(baseClass, false)
                }
                .forEach { candidate ->
                    ProgressManager.checkCanceled()
                    consumer.process(candidate)
                }
        }
    }
}
