// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenameJavaMethodProcessor
import org.jetbrains.kotlin.references.fe10.Fe10SyntheticPropertyAccessorReference
import org.jetbrains.kotlin.asJava.canHaveSyntheticGetter
import org.jetbrains.kotlin.asJava.canHaveSyntheticSetter
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.syntheticGetter
import org.jetbrains.kotlin.idea.references.SyntheticPropertyAccessorReference
import org.jetbrains.kotlin.idea.search.restrictToKotlinSources
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.filterIsInstanceWithChecker

class KotlinAwareJavaGetterRenameProcessor : RenameJavaMethodProcessor() {
    override fun canProcessElement(element: PsiElement) =
        super.canProcessElement(element) && element !is KtLightMethod && element.cast<PsiMethod>().canHaveSyntheticGetter

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean
    ): Collection<PsiReference> {
        val getters = super.findReferences(element, searchScope, searchInCommentsAndStrings)
        val setters = findSetterReferences(element, searchScope, searchInCommentsAndStrings).orEmpty()
        return getters + setters
    }

    private fun findSetterReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean
    ): Collection<PsiReference>? {
        val getter = element as? PsiMethod ?: return null
        val propertyName = getter.syntheticGetter ?: return null
        val containingClass = getter.containingClass ?: return null
        val setterName = JvmAbi.setterName(propertyName.asString())
        val restrictedToKotlinScope by lazy { searchScope.restrictToKotlinSources() }
        return containingClass
            .findMethodsByName(setterName, false)
            .filter { it.canHaveSyntheticSetter }
            .asSequence()
            .flatMap {
                super.findReferences(it, restrictedToKotlinScope, searchInCommentsAndStrings)
                    .filterIsInstanceWithChecker<SyntheticPropertyAccessorReference> { accessor -> !accessor.getter }
            }
            .map {
                Fe10SyntheticPropertyAccessorReference(it.expression, getter = true)
            }
            .toList()
    }
}
