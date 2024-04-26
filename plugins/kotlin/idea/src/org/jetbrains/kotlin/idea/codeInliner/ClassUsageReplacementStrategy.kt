// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInliner

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.impl.TypeAliasConstructorDescriptor
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.idea.util.replaceOrCreateTypeArgumentList
import org.jetbrains.kotlin.ir.expressions.typeParametersCount
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

class ClassUsageReplacementStrategy(
    typeReplacement: KtUserType?,
    constructorReplacement: CodeToInline?,
    project: Project
) : UsageReplacementStrategy {

    private val factory = KtPsiFactory(project)

    private val typeReplacement = typeReplacement?.takeIf { it.referenceExpression != null }
    private val typeReplacementQualifierAsExpression = typeReplacement?.qualifier?.let { factory.createExpression(it.text) }

    private val constructorReplacementStrategy = constructorReplacement?.let {
        CallableUsageReplacementStrategy(it, inlineSetter = false)
    }

    override fun createReplacer(usage: KtReferenceExpression): (() -> KtElement?)? {
        if (usage !is KtNameReferenceExpression) return null

        constructorReplacementStrategy?.createReplacer(usage)?.let { return it }

        when (val parent = usage.parent) {
            is KtUserType -> {
                if (typeReplacement == null) return null
                return {
                    val oldArgumentList = parent.typeArgumentList?.copy() as? KtTypeArgumentList
                    val replaced = parent.replaced(typeReplacement)
                    val newArgumentList = replaced.typeArgumentList
                    if (oldArgumentList != null && oldArgumentList.arguments.size == newArgumentList?.arguments?.size) {
                        newArgumentList.replace(oldArgumentList)
                    }

                    ShortenReferences.DEFAULT.process(replaced)
                }
            }

            is KtCallElement -> {
                if (usage != parent.calleeExpression) return null
                when {
                    constructorReplacementStrategy == null && typeReplacement != null -> return {
                        replaceConstructorCallWithOtherTypeConstruction(parent)
                    }
                    else -> return null
                }
            }

            else -> {
                if (typeReplacement != null) {
                    val fqNameStr = typeReplacement.text
                    val fqName = FqName(fqNameStr)

                    return {
                        usage.mainReference.bindToFqName(fqName, KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING) as? KtElement
                    }
                }

                return null
            }
        }
    }

    private fun replaceConstructorCallWithOtherTypeConstruction(callExpression: KtCallElement): KtElement {
        val referenceExpression = typeReplacement?.referenceExpression ?: error("Couldn't find referenceExpression")
        val classFromReplacement = KotlinClassShortNameIndex
            .get(referenceExpression.text, callExpression.project, callExpression.resolveScope)
            .firstOrNull()

        val replacementTypeArgumentList = typeReplacement.typeArgumentList
        val replacementTypeArgumentCount = classFromReplacement?.typeParameters?.size
            ?: replacementTypeArgumentList?.arguments?.size

        val typeArgumentList = callExpression.typeArgumentList
        val callDescriptor = callExpression.resolveToCall()
            ?.resultingDescriptor
            ?.let { if (it is TypeAliasConstructorDescriptor) it.underlyingConstructorDescriptor else it }
        val typeArgumentCount = callDescriptor?.typeParametersCount ?: typeArgumentList?.arguments?.size

        if (typeArgumentCount != replacementTypeArgumentCount) {
            if (replacementTypeArgumentList == null) typeArgumentList?.delete()
            else callExpression.replaceOrCreateTypeArgumentList(
                replacementTypeArgumentList.copy() as KtTypeArgumentList
            )
        }

        callExpression.calleeExpression?.replace(referenceExpression)

        val expressionToReplace = callExpression.getQualifiedExpressionForSelector() ?: callExpression
        val newExpression = if (typeReplacementQualifierAsExpression != null)
            factory.createExpressionByPattern("$0.$1", typeReplacementQualifierAsExpression, callExpression)
        else
            callExpression

        val result = if (expressionToReplace != newExpression) {
            expressionToReplace.replaced(newExpression)
        } else {
            expressionToReplace
        }

        return ShortenReferences.DEFAULT.process(result)
    }
}