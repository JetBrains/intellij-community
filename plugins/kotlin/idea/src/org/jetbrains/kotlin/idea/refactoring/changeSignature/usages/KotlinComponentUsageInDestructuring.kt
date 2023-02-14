// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.usages

import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.refactoring.replaceListPsiAndKeepDelimiters
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.buildDestructuringDeclaration
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.utils.ifEmpty

class KotlinComponentUsageInDestructuring(element: KtDestructuringDeclarationEntry) :
    KotlinUsageInfo<KtDestructuringDeclarationEntry>(element) {
    override fun processUsage(
        changeInfo: KotlinChangeInfo,
        element: KtDestructuringDeclarationEntry,
        allUsages: Array<out UsageInfo>
    ): Boolean {
        if (!changeInfo.isParameterSetOrOrderChanged) return true

        val declaration = element.parent as KtDestructuringDeclaration
        val currentEntries = declaration.entries
        val newParameterInfos = changeInfo.getNonReceiverParameters()

        val newDestructuring = KtPsiFactory(element.project).buildDestructuringDeclaration {
            val lastIndex = newParameterInfos.indexOfLast { it.oldIndex in currentEntries.indices }
            val nameValidator = CollectingNameValidator(
                filter = Fe10KotlinNewDeclarationNameValidator(
                    declaration.parent.parent,
                    null,
                    KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
                )
            )

            appendFixedText("val (")
            for (i in 0..lastIndex) {
                if (i > 0) {
                    appendFixedText(", ")
                }

                val paramInfo = newParameterInfos[i]
                val oldIndex = paramInfo.oldIndex
                if (oldIndex >= 0 && oldIndex < currentEntries.size) {
                    appendChildRange(PsiChildRange.singleElement(currentEntries[oldIndex]))
                } else {
                    appendFixedText(Fe10KotlinNameSuggester.suggestNameByName(paramInfo.name, nameValidator))
                }
            }
            appendFixedText(")")
        }
        replaceListPsiAndKeepDelimiters(
            changeInfo,
            declaration,
            newDestructuring,
            {
                apply {
                    val oldEntries = entries.ifEmpty { return@apply }
                    val firstOldEntry = oldEntries.first()
                    val lastOldEntry = oldEntries.last()
                    val newEntries = it.entries
                    if (newEntries.isNotEmpty()) {
                        addRangeBefore(newEntries.first(), newEntries.last(), firstOldEntry)
                    }
                    deleteChildRange(firstOldEntry, lastOldEntry)
                }
            },
            { entries }
        )

        return true
    }
}