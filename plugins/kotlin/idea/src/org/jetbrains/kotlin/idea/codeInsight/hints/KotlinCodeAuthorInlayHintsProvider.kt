// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.VcsCodeAuthorInlayHintsProvider
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.CLASS_LOCATION
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.FUNCTION_LOCATION
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.logCodeAuthorClicked
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class KotlinCodeAuthorInlayHintsProvider : VcsCodeAuthorInlayHintsProvider() {

    override fun isAccepted(element: PsiElement): Boolean =
        when (element) {
            is KtClassOrObject -> isAcceptedClassOrObject(element)
            is KtNamedFunction -> element.isTopLevel || isAcceptedClassOrObject(element.containingClassOrObject)
            is KtSecondaryConstructor -> true
            is KtClassInitializer -> true
            else -> false
        }

    private fun isAcceptedClassOrObject(element: KtClassOrObject?): Boolean =
        when (element) {
            is KtClass -> element !is KtEnumEntry
            is KtObjectDeclaration -> !element.isObjectLiteral()
            else -> false
        }

    override fun getClickHandler(element: PsiElement): () -> Unit {
        val project = element.project
        val location = if (element is KtClassOrObject) CLASS_LOCATION else FUNCTION_LOCATION

        return { logCodeAuthorClicked(project, location) }
    }
}