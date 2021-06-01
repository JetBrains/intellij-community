// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.openapi.project.Project
import com.intellij.usages.PsiNamedElementUsageGroupBase
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.impl.FileStructureGroupRuleProvider
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageGroupingRule
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.parents

class KotlinDeclarationGroupingRule(val level: Int = 0) : UsageGroupingRule {
    override fun groupUsage(usage: Usage): UsageGroup? {
        var element = (usage as? PsiElementUsage)?.element ?: return null

        if (element.containingFile !is KtFile) return null

        if (element is KtFile) {
            val offset = (usage as? UsageInfo2UsageAdapter)?.usageInfo?.navigationOffset
            if (offset != null) {
                element = element.findElementAt(offset) ?: element
            }
        }

        val parentList = element.parents.filterIsInstance<KtNamedDeclaration>().filterNot { it is KtProperty && it.isLocal }.toList()
        if (parentList.size <= level) {
            return null
        }
        return PsiNamedElementUsageGroupBase(parentList[parentList.size - level - 1])
    }
}

class KotlinDeclarationGroupRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule = KotlinDeclarationGroupingRule(0)
}

class KotlinDeclarationSecondLevelGroupRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule = KotlinDeclarationGroupingRule(1)
}
