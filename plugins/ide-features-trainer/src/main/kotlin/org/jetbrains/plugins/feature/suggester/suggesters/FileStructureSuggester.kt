package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.EditorFindAction
import org.jetbrains.plugins.feature.suggester.actions.EditorFocusGainedAction
import org.jetbrains.plugins.feature.suggester.actionsLocalSummary
import org.jetbrains.plugins.feature.suggester.createTipSuggestion
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
import java.util.concurrent.TimeUnit

class FileStructureSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Finding a definition can be faster using the File Structure action."
        const val SUGGESTING_ACTION_ID = "FileStructurePopup"
        const val SUGGESTING_TIP_FILENAME = "neue-FileStructurePopup.html"
    }

    private val actionsSummary = actionsLocalSummary()
    override val languages = listOf("JAVA", "kotlin", "Python", "ECMAScript 6")

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        if (actions.size < 2) return NoSuggestion
        val action = actions.lastOrNull()!!
        val language = action.language ?: return NoSuggestion
        val langSupport = LanguageSupport.getForLanguage(language) ?: return NoSuggestion
        when (action) {
            is EditorFocusGainedAction -> {
                if (actions.get(1) !is EditorFindAction) return NoSuggestion // check that previous action is Find
                val psiFile = action.psiFile ?: return NoSuggestion
                val project = action.project ?: return NoSuggestion
                val editor = action.editor ?: return NoSuggestion

                val findModel = getFindModel(project)
                val textToFind = findModel.stringToFind
                val definition = langSupport.getDefinitionOnCaret(psiFile, editor.caretModel.offset)
                if (definition is PsiNamedElement && langSupport.isFileStructureElement(definition) &&
                    definition.name?.contains(textToFind, !findModel.isCaseSensitive) == true
                ) {
                    return createTipSuggestion(
                        createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                        suggestingActionDisplayName,
                        SUGGESTING_TIP_FILENAME
                    )
                }
            }
            else -> NoSuggestion
        }

        return NoSuggestion
    }

    override fun isSuggestionNeeded(minNotificationIntervalDays: Int): Boolean {
        return super.isSuggestionNeeded(
            actionsSummary,
            SUGGESTING_ACTION_ID,
            TimeUnit.DAYS.toMillis(minNotificationIntervalDays.toLong())
        )
    }

    private fun LanguageSupport.getDefinitionOnCaret(psiFile: PsiFile, caretOffset: Int): PsiElement? {
        val offset = caretOffset - 1
        if (offset < 0) return null
        val curElement = psiFile.findElementAt(offset)
        return if (curElement != null && isIdentifier(curElement)) {
            curElement.parent
        } else {
            null
        }
    }

    private fun getFindModel(project: Project): FindModel {
        val findManager = FindManager.getInstance(project)
        val findModel = FindModel()
        findModel.copyFrom(findManager.findInFileModel)
        return findModel
    }

    override val id: String = "File structure"

    override val suggestingActionDisplayName: String = "File structure"
}
