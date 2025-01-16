// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.projectStructure.getKotlinSourceRootType
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator
import org.jetbrains.kotlin.idea.core.util.KotlinIdeaCoreBundle
import org.jetbrains.kotlin.psi.KtFile

class K2EnableUnsupportedFeatureFix(
    element: PsiElement,
    private val module: Module,
    private val languageFeature: LanguageFeature,
    private val alternativeActionText: @IntentionName String? = null,
) : KotlinQuickFixAction<PsiElement>(element) {
    private val configurator: KotlinProjectConfigurator? by lazy {
        KotlinProjectConfigurator.EP_NAME
            .lazySequence()
            .firstOrNull { it.isApplicable(module) }
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val isTestFile = isFileUnderTestRoot(project, file)
        configurator?.changeGeneralFeatureConfiguration(module, languageFeature, LanguageFeature.State.ENABLED, isTestFile)
    }

    override fun getText(): @IntentionName String {
        val actionText = alternativeActionText
            ?: KotlinIdeaCoreBundle.message("fix.enable.feature.support.text", languageFeature.presentableName)
        return actionText
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinIdeaCoreBundle.message("fix.enable.feature.support.family", languageFeature.presentableName)
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        return configurator != null
    }

    // Kotlin Facet doesn't store info about test modules correctly
    private fun isFileUnderTestRoot(project: Project, file: KtFile): Boolean {
        val virtualFile = file.virtualFile ?: return false
        return ProjectFileIndex.getInstance(project).getKotlinSourceRootType(virtualFile) == TestSourceKotlinRootType
    }
}
