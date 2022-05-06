// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@Suppress("IntentionDescriptionNotFoundInspection")
class EnableKotlinTypeHintsOption : IntentionAction, HighPriorityAction {
    @IntentionName
    private var lastOptionName = ""

    override fun getText(): String = lastOptionName

    override fun getFamilyName(): String = CodeInsightBundle.message("inlay.hints.intention.family.name")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val element = findElement(editor, file) ?: return false

        lastOptionName = ""
        for (hintType in HintType.values()) {
            findSetting(hintType, project, element)?.let {
                val enabled = it.isEnabled(hintType)
                lastOptionName = if (enabled) hintType.hideDescription else hintType.showDescription
                return true
            }
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = findElement(editor, file) ?: return

        for (hintType in HintType.values()) {
            findSetting(hintType, project, element)?.let {
                val enabled = it.isEnabled(hintType)
                it.enable(hintType, !enabled)
                InlayHintsPassFactory.forceHintsUpdateOnNextPass()
                return
            }
        }
    }

    override fun startInWriteAction(): Boolean = false

    private fun findElement(editor: Editor, file: PsiFile): PsiElement? {
        if (file !is KtFile) return null
        val offset = editor.caretModel.offset
        val leaf1 = file.findElementAt(offset)
        val leaf2 = file.findElementAt(offset - 1)
        return if (leaf1 != null && leaf2 != null) PsiTreeUtil.findCommonParent(leaf1, leaf2) else null
    }

    private fun findSetting(hintType: HintType, project: Project, element: PsiElement): KotlinAbstractHintsProvider.HintsSettings? {
        if (!hintType.isApplicable(element)) return null
        val hintsSettings = InlayHintsSettings.instance()
        val providerInfos = InlayHintsProviderFactory.EP.extensionList
            .flatMap { it.getProvidersInfo(project) }
            .filter { it.language == KotlinLanguage.INSTANCE }
        val provider = providerInfos
            .firstOrNull {
                val hintsProvider = it.provider as? KotlinAbstractHintsProvider ?: return@firstOrNull false
                hintsProvider.isHintSupported(hintType)
            }
            ?.provider?.safeAs<KotlinAbstractHintsProvider<KotlinAbstractHintsProvider.HintsSettings>>() ?: return null
        val settingsKey = provider.key
        return hintsSettings.findSettings(settingsKey, KotlinLanguage.INSTANCE, provider::createSettings)
    }
}