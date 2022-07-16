// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.findUsages

import com.intellij.java.indexing.JavaIndexingBundle
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.util.Processor
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinInnerClassInheritorsSearcher: QueryExecutorBase<PsiClass, ClassInheritorsSearch.SearchParameters>() {
    override fun processQuery(queryParameters: ClassInheritorsSearch.SearchParameters, consumer: Processor<in PsiClass>) {
        val searchScope = queryParameters.scope.safeAs<LocalSearchScope>() ?: return
        val classToProcess = queryParameters.classToProcess
        val baseClass = classToProcess.safeAs<KtLightClassForSourceDeclaration>() ?: return

        val kotlinOrigin = baseClass.kotlinOrigin
        if (runReadAction { kotlinOrigin.isTopLevel() || kotlinOrigin.isLocal || !kotlinOrigin.isPrivate() }) return

        val progress = ProgressIndicatorProvider.getGlobalProgressIndicator()
        if (progress != null) {
            progress.pushState()
            progress.text =
                runReadAction { baseClass.name }?.let { JavaIndexingBundle.message("psi.search.inheritors.of.class.progress", it) }
                    ?: JavaIndexingBundle.message("psi.search.inheritors.progress")
        }

        try {
            for (element in searchScope.scope) {
                ProgressManager.checkCanceled()
                if (!runReadAction {
                    val declarations = element.safeAs<KtClassOrObject>()?.declarations ?: return@runReadAction true
                    for (declaration in declarations) {
                        val ktClassOrObject =
                            declaration.safeAs<KtClassOrObject>()?.takeIf { it.superTypeListEntries.isNotEmpty() } ?: continue
                        ktClassOrObject.toLightClass()?.let {
                            if (it.isInheritor(classToProcess, true) && !consumer.process(it)) {
                                return@runReadAction false
                            }
                        }
                    }
                    true
                }) break
            }
        } finally {
            progress?.popState()
        }
    }
}