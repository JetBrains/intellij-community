// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages.handlers

import com.intellij.find.findUsages.AbstractFindUsagesDialog
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.findUsages.dialogs.KotlinTypeParameterFindUsagesDialog
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class KotlinTypeParameterFindUsagesHandler(
    element: KtNamedDeclaration,
    factory: KotlinFindUsagesHandlerFactory
) : KotlinFindUsagesHandler<KtNamedDeclaration>(element, factory) {
    override fun getFindUsagesDialog(
        isSingleFile: Boolean, toShowInNewTab: Boolean, mustOpenInNewTab: Boolean
    ): AbstractFindUsagesDialog {
        return KotlinTypeParameterFindUsagesDialog<KtNamedDeclaration>(
            getElement(), project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, this
        )
    }

    override fun createSearcher(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Searcher {
        return object : Searcher(element, processor, options) {
            override fun buildTaskList(forHighlight: Boolean): Boolean {
                addTask {
                    ReferencesSearch.search(element, options.searchScope).all { processUsage(processor, it) }
                }
                return true
            }
        }
    }

    override fun getFindUsagesOptions(dataContext: DataContext?): FindUsagesOptions {
        return factory.defaultOptions
    }
}
