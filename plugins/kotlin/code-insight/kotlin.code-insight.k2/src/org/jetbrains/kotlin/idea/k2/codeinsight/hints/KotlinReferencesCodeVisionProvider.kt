// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderBase
import com.intellij.codeInsight.hints.codeVision.RenameAwareReferencesCodeVisionProvider
import com.intellij.java.JavaBundle
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.statistics.KotlinCodeVisionUsagesCollector
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

class KotlinReferencesCodeVisionProvider : RenameAwareReferencesCodeVisionProvider() {
    companion object {
        const val ID: String = "kotlin.references"
    }

    override fun acceptsFile(file: PsiFile): Boolean = file.language == KotlinLanguage.INSTANCE

    override fun acceptsElement(element: PsiElement): Boolean =
        element is KtNamedFunction && !element.isLocal || element is KtProperty && !element.isLocal || element is KtClassLikeDeclaration

    private fun getVisionInfo(element: PsiElement, file: PsiFile): CodeVisionProviderBase.CodeVisionInfo? {
        val namedDeclaration = element as? KtNamedDeclaration ?: return null
        val result = KotlinUsagesCountManager.getInstance(element.getProject()).countMemberUsages(file, namedDeclaration)
        if (result == 0) return null
        return return CodeVisionProviderBase.CodeVisionInfo(KotlinBundle.message("hints.codevision.usages.format", result), result)
    }

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        return getVisionInfo(element, file)?.text
    }

    override fun logClickToFUS(element: PsiElement, hint: String) {
        KotlinCodeVisionUsagesCollector.logUsagesClicked(element.project)
    }

    override val name: String
        get() = JavaBundle.message("settings.inlay.java.usages")
    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingBefore("kotlin.inheritors"))
    override val id: String
        get() = ID
}
