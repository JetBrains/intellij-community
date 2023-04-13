// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.VcsCodeAuthorInlayHintsProvider
import com.intellij.lang.Language
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.CLASS_LOCATION
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.FUNCTION_LOCATION
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.logCodeAuthorClicked
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class KotlinCodeAuthorInlayHintsProvider : VcsCodeAuthorInlayHintsProvider() {

    override fun isLanguageSupported(language: Language): Boolean = language == KotlinLanguage.INSTANCE

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

    override val description: String
      get() = VcsBundle.message("inlay.vcs.code.author.description")

    override fun getProperty(key: String): String = KotlinBundle.getMessage(key)

    override fun getCaseDescription(case: ImmediateConfigurable.Case): String? = case.extendedDescription

}