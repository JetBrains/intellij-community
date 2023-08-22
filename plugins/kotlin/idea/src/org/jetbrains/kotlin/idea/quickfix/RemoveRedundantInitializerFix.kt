// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtProperty

class RemoveRedundantInitializerFix(element: KtProperty) : RemovePartsFromPropertyFix(element, true, false, false) {

    override fun getText(): String = KotlinBundle.message("remove.redundant.initializer")

    override fun getFamilyName(): String = text

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtProperty>? {
            val element = diagnostic.psiElement
            val property = PsiTreeUtil.getParentOfType(element, KtProperty::class.java) ?: return null
            return RemoveRedundantInitializerFix(property)
        }
    }

}