// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.psi.*
import com.intellij.psi.impl.search.MethodTextOccurrenceProcessor
import com.intellij.psi.impl.search.MethodUsagesSearcher
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.Processor
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.asJava.canHaveOverride
import org.jetbrains.kotlin.asJava.canHaveSyntheticGetter
import org.jetbrains.kotlin.asJava.canHaveSyntheticSetter
import org.jetbrains.kotlin.asJava.syntheticAccessors
import org.jetbrains.kotlin.idea.base.util.restrictToKotlinSources
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.SyntheticPropertyAccessorReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

class KotlinOverridingMethodReferenceSearcher : MethodUsagesSearcher() {
    override fun processQuery(p: MethodReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val method = p.method
        val canOverride = p.project.runReadActionInSmartMode { method.canHaveOverride }
        if (!canOverride) {
            return
        }

        val searchScope = p.project.runReadActionInSmartMode {
            p.effectiveSearchScope.restrictToKotlinSources()
        }

        if (SearchScope.isEmptyScope(searchScope)) return

        super.processQuery(MethodReferencesSearch.SearchParameters(method, searchScope, p.isStrictSignatureSearch, p.optimizer), consumer)

        p.project.runReadActionInSmartMode {
            val containingClass = method.containingClass ?: return@runReadActionInSmartMode
            val syntheticAssessors = method.syntheticAccessors.ifEmpty { return@runReadActionInSmartMode }

            val processor = getTextOccurrenceProcessor(arrayOf(method), containingClass, false)
            for (name in syntheticAssessors) {
                p.optimizer.searchWord(
                    name.asString(),
                    searchScope,
                    UsageSearchContext.IN_CODE,
                    true,
                    method,
                    processor,
                )
            }
        }
    }

    override fun getTextOccurrenceProcessor(
        methods: Array<out PsiMethod>,
        aClass: PsiClass,
        strictSignatureSearch: Boolean,
    ): MethodTextOccurrenceProcessor = object : MethodTextOccurrenceProcessor(aClass, strictSignatureSearch, *methods) {
        override fun processInexactReference(
            ref: PsiReference,
            refElement: PsiElement?,
            method: PsiMethod,
            consumer: Processor<in PsiReference>
        ): Boolean {
            fun isWrongAccessorReference(): Boolean {
                val canHaveGetter by lazy { method.canHaveSyntheticGetter }
                val canHaveSetter by lazy { method.canHaveSyntheticSetter }

                if (ref is KtSimpleNameReference) {
                    val readWriteAccess = ref.expression.readWriteAccess(true)
                    return readWriteAccess.isRead == canHaveSetter && readWriteAccess.isWrite == canHaveGetter
                }

                if (ref is SyntheticPropertyAccessorReference) {
                    return if (ref.getter) !canHaveGetter else !canHaveSetter
                }

                return false
            }

            if (refElement !is KtCallableDeclaration) {
                if (isWrongAccessorReference()) return true
                if (refElement !is PsiMethod) return true

                val refMethodClass = refElement.containingClass ?: return true
                val substitutor = TypeConversionUtil.getClassSubstitutor(myContainingClass, refMethodClass, PsiSubstitutor.EMPTY)
                if (substitutor != null) {
                    val superSignature = method.getSignature(substitutor)
                    val refSignature = refElement.getSignature(PsiSubstitutor.EMPTY)

                    if (MethodSignatureUtil.isSubsignature(superSignature, refSignature)) {
                        return super.processInexactReference(ref, refElement, method, consumer)
                    }
                }
                return true
            }

            val lightMethods = when (refElement) {
                is KtProperty, is KtParameter -> {
                    if (isWrongAccessorReference()) return true
                    val isGetter = JvmAbi.isGetterName(method.name)
                    refElement.toLightMethods().filter { JvmAbi.isGetterName(it.name) == isGetter }
                }

                is KtNamedFunction -> refElement.toLightMethods().filter { it.name == method.name }
                else -> refElement.toLightMethods()
            }

            return lightMethods.all { super.processInexactReference(ref, it, method, consumer) }
        }
    }
}