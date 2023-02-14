// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@Suppress("IntentionDescriptionNotFoundInspection")
class KotlinInlayHintToggleAction : IntentionAction, LowPriorityAction {
    @IntentionName
    private var lastOptionName = ""

    override fun getText(): String = lastOptionName

    override fun getFamilyName(): String = KotlinBundle.message("hints.types")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        lastOptionName = ""
        var element = findElement(editor, file)

        while (element != null) {
            for (hintType in Holder.hintTypes) {
                if (!hintType.isApplicable(element)) continue
                findSetting(hintType, project)?.let {
                    val enabled = it.second.isEnabled(hintType)
                    lastOptionName = if (enabled) hintType.hideDescription else hintType.showDescription
                    return true
                }
            }
            element = element.parent
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        var element = findElement(editor, file)

        while (element != null) {
            for (hintType in Holder.hintTypes) {
                if (toggleHintSetting(hintType, project, element)) return
            }
            element = element.parent
        }
    }

    private fun findElement(editor: Editor, file: PsiFile): PsiElement? {
        val ktFile = file as? KtFile ?: return null
        val offset = editor.caretModel.offset
        return ktFile.findElementAt(offset - 1)
    }

    override fun startInWriteAction(): Boolean = false

    private object Holder {
        val hintTypes: Array<HintType> = arrayOf(
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
    }
}

internal fun toggleHintSetting(
    hintType: HintType,
    project: Project,
    element: PsiElement,
    state: (KotlinAbstractHintsProvider.HintsSettings) -> Boolean = { setting -> !setting.isEnabled(hintType) }
): Boolean {
    if (!hintType.isApplicable(element)) return false
    val hintsSettings = InlayHintsSettings.instance()
    return findSetting(hintType, project, hintsSettings)?.let {
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
        true
    } ?: false
}

private fun findSetting(hintType: HintType, project: Project, hintsSettings: InlayHintsSettings = InlayHintsSettings.instance()):
        Pair<SettingsKey<KotlinAbstractHintsProvider.HintsSettings>, KotlinAbstractHintsProvider.HintsSettings>? {
    val language = KotlinLanguage.INSTANCE
    val providerInfos =
        InlayHintsProviderFactory.EP.extensionList
            .flatMap { it.getProvidersInfo() }
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