// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

class K2CreateFromUsageUnresolvedReferenceQuickFixProvider: UnresolvedReferenceQuickFixProvider<PsiReference>() {
    override fun registerFixes(ref: PsiReference, registrar: QuickFixActionRegistrar) {
        when (val element = ref.element) {
            is KtElement -> {
                try {
                    K2CreateFunctionFromUsageBuilder.generateCreateMethodActions(element).forEach(registrar::register)
                    K2CreatePropertyFromUsageBuilder.generateCreatePropertyActions(element).forEach(registrar::register)
                    K2CreateLocalVariableFromUsageBuilder.generateCreateLocalVariableAction(element)?.let(registrar::register)
                    K2CreateParameterFromUsageBuilder.generateCreateParameterAction(element)?.map (registrar::register)
                    K2CreateClassFromUsageBuilder.generateCreateClassActions(element).forEach(registrar::register)
                } catch (e: Exception) {
                    if (Logger.shouldRethrow(e)) throw e
                    throw KotlinExceptionWithAttachments("Failed to create quick fixes for unresolved reference", e)
                        .withPsiAttachment("reference.txt", element)
                        .withPsiAttachment("file.kt", element.containingFile)
                }
            }
        }
    }

    override fun getReferenceClass(): Class<PsiReference> = PsiReference::class.java
}