// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.hints.codeVision.UsageCounterConfigurationBase
import com.intellij.codeInsight.hints.codeVision.UsagesCountManagerBase
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.intellij.util.ui.EDT
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinClassFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFunctionFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinPropertyFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.handlers.KotlinFindClassUsagesHandler
import org.jetbrains.kotlin.idea.base.searching.usages.handlers.KotlinFindMemberUsagesHandler
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isCheapEnoughToSearchUsages
import org.jetbrains.kotlin.psi.*
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.PROJECT)
class KotlinUsagesCountManager(project: Project): UsagesCountManagerBase<KtNamedDeclaration>(project, KotlinUsageCounterConfiguration) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): KotlinUsagesCountManager {
      return project.getService(KotlinUsagesCountManager::class.java)
    }
  }

  override fun findSupers(member: KtNamedDeclaration): List<KtNamedDeclaration> = listOf(member)
  override fun getKey(member: KtNamedDeclaration): String? = member.fqName?.asString()
}

object KotlinUsageCounterConfiguration: UsageCounterConfigurationBase<KtNamedDeclaration> {
  override fun countUsages(file: PsiFile, members: List<KtNamedDeclaration>, scope: SearchScope): Int {
     return members.sumOf { usageCount(it, file, scope) }
  }
}

@OptIn(KaAllowAnalysisOnEdt::class)
private fun usageCount(
    namedDeclaration: KtNamedDeclaration,
    file: PsiFile,
    scope: SearchScope,
): Int {
    val isCheapEnough = isCheapEnoughToSearchUsages(namedDeclaration)
    if (isCheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) {
        return 0
    } else if (isCheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
        return 0
    }
    val count = AtomicInteger(0)
    val processor = object : Processor<UsageInfo> {
        override fun process(t: UsageInfo?): Boolean {
            count.incrementAndGet()
            return true
        }
    }
    val project = file.project
    when (namedDeclaration) {
        is KtClassOrObject -> {
            val findClassUsagesHandler = KotlinFindClassUsagesHandler(namedDeclaration, KotlinFindUsagesHandlerFactory(project))
            val options = KotlinClassFindUsagesOptions(project)
            options.searchScope = scope
            options.isSearchForTextOccurrences = false // ignore text occurrences which are enabled by default
            findClassUsagesHandler.processElementUsages(namedDeclaration, processor, options)
        }

        is KtCallableDeclaration -> {
            val findClassUsagesHandler =
                KotlinFindMemberUsagesHandler.getInstance(namedDeclaration, factory = KotlinFindUsagesHandlerFactory(project))
            val options = when (namedDeclaration) {
                is KtFunction -> KotlinFunctionFindUsagesOptions(project)
                is KtProperty -> KotlinPropertyFindUsagesOptions(project)
                else -> return 0
            }
            options.searchScope = scope
            allowAnalysisOnEdt {
                require(!EDT.isCurrentThreadEdt() || CodeVisionHost.isCodeLensTest())
                findClassUsagesHandler.processElementUsages(namedDeclaration, processor, options)
            }
        }

        else -> {
            return 0
        }
    }
    return count.get()
}