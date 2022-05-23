// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@Suppress("IntentionDescriptionNotFoundInspection")
class KotlinInlayHintToggleAction : IntentionAction, HighPriorityAction {
    private val hintTypes = arrayOf(
        HintType.RANGES,
        HintType.PROPERTY_HINT,
        HintType.LOCAL_VARIABLE_HINT,
        HintType.FUNCTION_HINT,
        HintType.PARAMETER_TYPE_HINT,
        HintType.PARAMETER_HINT,
        HintType.LAMBDA_RETURN_EXPRESSION,
        HintType.LAMBDA_IMPLICIT_PARAMETER_RECEIVER,
        HintType.SUSPENDING_CALL,
    )
    @IntentionName
    private var lastOptionName = ""

    override fun getText(): String = lastOptionName

    override fun getFamilyName(): String = KotlinBundle.message("hints.types")
    
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val element = findElement(editor, file) ?: return false
        
        lastOptionName = ""
        for (hintType in hintTypes) {
            if (!hintType.isApplicable(element)) continue
            findSetting(hintType, project)?.let {
                val enabled = it.second.isEnabled(hintType)
                lastOptionName = if (enabled) hintType.hideDescription else hintType.showDescription
                return true
            }
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = findElement(editor, file) ?: return

        for (hintType in hintTypes) {
            toggleHintSetting(hintType, project, element)
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

}

internal fun toggleHintSetting(
    hintType: HintType,
    project: Project,
    element: PsiElement,
    state: (KotlinAbstractHintsProvider.HintsSettings) -> Boolean = { setting -> !setting.isEnabled(hintType) }
) {
    if (!hintType.isApplicable(element)) return
    val hintsSettings = InlayHintsSettings.instance()
    findSetting(hintType, project, hintsSettings)?.let {
        val settingsKey = it.first
        val settings = it.second
        val enable = state(settings)
        val language = KotlinLanguage.INSTANCE
        if (enable) {
            InlayHintsSettings.instance().changeHintTypeStatus(settingsKey, language, true)
        }
        settings.enable(hintType, enable)
        hintsSettings.storeSettings(settingsKey, language, settings)
        refreshHints()
        return
    }
}
private fun findSetting(hintType: HintType, project: Project, hintsSettings: InlayHintsSettings = InlayHintsSettings.instance()):
        Pair<SettingsKey<KotlinAbstractHintsProvider.HintsSettings>, KotlinAbstractHintsProvider.HintsSettings>? {
    val language = KotlinLanguage.INSTANCE
    val providerInfos =
        InlayHintsProviderFactory.EP.extensionList
            .flatMap { it.getProvidersInfo(project) }
            .filter { it.language == language }
    val provider = providerInfos
        .firstOrNull {
            val hintsProvider = it.provider as? KotlinAbstractHintsProvider ?: return@firstOrNull false
            hintsProvider.isHintSupported(hintType)
        }
        ?.provider?.safeAs<KotlinAbstractHintsProvider<KotlinAbstractHintsProvider.HintsSettings>>() ?: return null
    val settingsKey = provider.key
    return settingsKey to hintsSettings.findSettings(settingsKey, language, provider::createSettings)
}