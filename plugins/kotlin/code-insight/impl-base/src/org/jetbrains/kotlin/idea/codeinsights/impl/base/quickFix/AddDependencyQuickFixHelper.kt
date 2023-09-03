// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.asJava.elements.KtLightMember
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.util.runWhenSmart
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset

object AddDependencyQuickFixHelper {
    fun createQuickFix(psiElement: PsiElement): List<IntentionAction> {
        val simpleExpression = psiElement as? KtSimpleNameExpression ?: return emptyList()
        if (!RootKindFilter.projectSources.matches(simpleExpression)) return emptyList()
        val project = simpleExpression.project

        val importDirective = simpleExpression.parentOfType<KtImportDirective>()
        val refElement: KtElement = simpleExpression.getQualifiedElement()

        val reference = object : PsiReferenceBase<KtElement>(refElement) {
            override fun resolve() = null

            override fun getVariants() = PsiReference.EMPTY_ARRAY

            override fun getRangeInElement(): TextRange {
                val offset = simpleExpression.startOffset - refElement.startOffset
                return TextRange(offset, offset + simpleExpression.textLength)
            }

            override fun getCanonicalText() = refElement.text

            override fun bindToElement(element: PsiElement): PsiElement {
                project.runWhenSmart {
                    project.executeWriteCommand("") {
                        simpleExpression.mainReference.bindToElement(element, KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING)
                    }
                }
                return element
            }
        }

        val registrar = mutableListOf<IntentionAction>()
        OrderEntryFix.registerFixes(reference, registrar) { shortName ->
            val scope = GlobalSearchScope.allScope(project)
            val classesByName = PsiShortNamesCache.getInstance(project).getClassesByName(shortName, scope)
            if (classesByName.isNotEmpty()) return@registerFixes classesByName
            val importedFqName = importDirective?.takeUnless { it.isAllUnder }?.importedFqName
            if (importedFqName != null) {
                PsiShortNamesCache.getInstance(project).getClassesByName(importedFqName.shortName().asString(), scope)
                    .takeUnless { it.isEmpty() }
                    ?.let { return@registerFixes it }
            }

            val importedFqNameAsString = importedFqName?.asString()
            val declarations =
                if (importedFqNameAsString != null) {
                    KotlinTopLevelPropertyFqnNameIndex[importedFqNameAsString, project, scope]
                } else {
                    KotlinPropertyShortNameIndex[shortName, project, scope].filter { (it as? KtProperty)?.isTopLevel == true }
                }.takeUnless { it.isEmpty() } ?:
                if (importedFqNameAsString != null) {
                    KotlinTopLevelFunctionFqnNameIndex[importedFqNameAsString, project, scope]
                } else {
                    KotlinFunctionShortNameIndex[shortName, project, scope].filter { (it as? KtNamedFunction)?.isTopLevel == true }
                }

            if (declarations.isNotEmpty()) {
                val lightClasses = declarations
                    .flatMap { it.toLightElements() }
                    .mapNotNull { (it as? KtLightMember<*>)?.containingClass }
                    .toSet()
                if (lightClasses.isNotEmpty()) {
                    return@registerFixes lightClasses.toTypedArray()
                }
            }

            PsiClass.EMPTY_ARRAY
        }
        return registrar
    }
}