// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.asJava.elements.KtLightMember
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement

object AddDependencyQuickFixHelper: QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
    override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
        val expression =
            when (psiElement) {
                is KtSimpleNameExpression -> psiElement
                is KtImportDirective -> psiElement.importedReference
                else -> null
            }
        if (expression == null || !RootKindFilter.projectSources.matches(expression)) return emptyList()
        val project = expression.project

        val importDirective = expression.parentOfType<KtImportDirective>()

        val refElement: KtElement =
            when (expression) {
                is KtSimpleNameExpression -> expression.getQualifiedElement()
                is KtQualifiedExpression -> expression
                else ->  return emptyList()
            }

        val reference = expression.mainReference ?: return emptyList()

        val registrar = mutableListOf<IntentionAction>()
        OrderEntryFix.registerFixes(reference, registrar, { shortName ->
            findDeclarationsByShortName(shortName, importDirective, project)
        }) {
            refElement.text
        }
        return registrar
    }
}

fun findDeclarationsByShortName(
    shortName: String,
    importDirective: KtImportDirective?,
    project: Project
): Array<out PsiMember> {
    val scope = GlobalSearchScope.allScope(project)
    val classesByName = PsiShortNamesCache.getInstance(project).getClassesByName(shortName, scope)
    if (classesByName.isNotEmpty()) return classesByName
    val importedFqName = importDirective?.takeUnless { it.isAllUnder }?.importedFqName
    if (importedFqName != null) {
        PsiShortNamesCache.getInstance(project).getClassesByName(importedFqName.shortName().asString(), scope)
            .takeUnless { it.isEmpty() }
            ?.let { return it }
    }

    val importedFqNameAsString = importedFqName?.asString()
    val declarations =
        if (importedFqNameAsString != null) {
            KotlinTopLevelPropertyFqnNameIndex[importedFqNameAsString, project, scope]
        } else {
            KotlinPropertyShortNameIndex[shortName, project, scope].filter { (it as? KtProperty)?.isTopLevel == true }
        }.takeUnless { it.isEmpty() } ?: if (importedFqNameAsString != null) {
            KotlinTopLevelFunctionFqnNameIndex[importedFqNameAsString, project, scope]
        } else {
            KotlinFunctionShortNameIndex[shortName, project, scope].filter { it.isTopLevel == true }
        }

    if (declarations.isNotEmpty()) {
        val lightClasses = declarations
            .flatMap { it.toLightElements() }
            .mapNotNull { it as? KtLightMember<*> }
            .toSet()
        if (lightClasses.isNotEmpty()) {
            return lightClasses.toTypedArray()
        }
    }

    return PsiClass.EMPTY_ARRAY
}