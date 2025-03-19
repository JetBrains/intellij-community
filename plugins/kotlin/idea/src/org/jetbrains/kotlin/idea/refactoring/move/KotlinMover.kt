// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.base.psi.deleteSingle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

@Deprecated("This declaration is K1 specific, please use the frontend-independent KotlinDeclarationMover instead.")
interface KotlinMover : (KtNamedDeclaration, KtElement) -> KtNamedDeclaration {
    @Deprecated("This declaration is K1 specific, please use the frontend-independent KotlinDeclarationMover instead.")
    object Default : KotlinMover {
        override fun invoke(originalElement: KtNamedDeclaration, targetContainer: KtElement): KtNamedDeclaration {
            @Suppress("DuplicatedCode")
            return when (targetContainer) {
                is KtFile -> {
                    val declarationContainer: KtElement =
                        if (targetContainer.isScript()) targetContainer.script!!.blockExpression else targetContainer
                    declarationContainer.add(originalElement) as KtNamedDeclaration
                }
                is KtClassOrObject -> targetContainer.addDeclaration(originalElement)
                else -> throw KotlinExceptionWithAttachments("Unexpected element")
                    .withAttachment("context", targetContainer.getElementTextWithContext())
            }.apply {
                val container = originalElement.containingClassOrObject
                if (container is KtObjectDeclaration
                    && container.isCompanion()
                    && container.declarations.singleOrNull() == originalElement
                    && ReferencesSearch.search(container, LocalSearchScope(container.containingFile)).findAll().isEmpty()
                ) {
                    container.deleteSingle()
                } else {
                    originalElement.deleteSingle()
                }
            }
        }
    }
}