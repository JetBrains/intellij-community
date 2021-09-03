// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.blockingCallsDetection

import com.intellij.codeInspection.blockingCallsDetection.BlockingMethodChecker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.project.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.hasSuspendModifier
import org.jetbrains.uast.toUElement

internal class CoroutineBlockingMethodChecker : BlockingMethodChecker {
    override fun isApplicable(file: PsiFile): Boolean {
        if (file !is KtFile) return false

        val languageVersionSettings = getLanguageVersionSettings(file)
        return languageVersionSettings.supportsFeature(LanguageFeature.ReleaseCoroutines)
    }

    override fun isMethodBlocking(method: PsiMethod): Boolean = false

    override fun isMethodNonBlocking(method: PsiMethod): Boolean {
        val uMethod = method.toUElement()
        val sourcePsi = uMethod?.sourcePsi ?: return false
        return sourcePsi is KtNamedFunction && sourcePsi.modifierList?.hasSuspendModifier() == true
    }

    private fun getLanguageVersionSettings(psiElement: PsiElement): LanguageVersionSettings {
        return psiElement.module?.languageVersionSettings ?: psiElement.project.getLanguageVersionSettings()
    }
}