package training.featuresSuggester.suggesters

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import training.featuresSuggester.FeatureSuggesterBundle
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.SuggesterSupport
import training.featuresSuggester.Suggestion
import training.featuresSuggester.actions.Action
import training.featuresSuggester.actions.EditorFindAction
import training.featuresSuggester.actions.EditorFocusGainedAction

class FileStructureSuggester : AbstractFeatureSuggester() {
  override val id: String = "File structure"
  override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("file.structure.name")

  override val message = FeatureSuggesterBundle.message("file.structure.message")
  override val suggestingActionId = "FileStructurePopup"
  override val suggestingTipFileName = "FileStructurePopup.html"
  override val minSuggestingIntervalDays = 14

  override val languages = listOf("JAVA", "kotlin", "Python", "ECMAScript 6")

  private var prevActionIsEditorFindAction = false

  override fun getSuggestion(action: Action): Suggestion {
    val language = action.language ?: return NoSuggestion
    val langSupport = SuggesterSupport.getForLanguage(language) ?: return NoSuggestion
    when (action) {
      is EditorFindAction -> {
        prevActionIsEditorFindAction = true
      }
      is EditorFocusGainedAction -> {
        if (!prevActionIsEditorFindAction) return NoSuggestion // check that previous action is Find
        val psiFile = action.psiFile ?: return NoSuggestion
        val project = action.project ?: return NoSuggestion
        val findModel = getFindModel(project)
        val textToFind = findModel.stringToFind
        val definition = langSupport.getDefinitionOnCaret(psiFile, action.editor.caretModel.offset)
        if (definition is PsiNamedElement && langSupport.isFileStructureElement(definition) &&
            definition.name?.contains(textToFind, !findModel.isCaseSensitive) == true
        ) {
          prevActionIsEditorFindAction = false
          return createSuggestion()
        }
      }
      else -> {
        prevActionIsEditorFindAction = false
        NoSuggestion
      }
    }

    return NoSuggestion
  }

  private fun SuggesterSupport.getDefinitionOnCaret(psiFile: PsiFile, caretOffset: Int): PsiElement? {
    val offset = caretOffset - 1
    if (offset < 0) return null
    val curElement = psiFile.findElementAt(offset)
    return if (curElement != null && isIdentifier(curElement)) {
      curElement.parent
    }
    else {
      null
    }
  }

  private fun getFindModel(project: Project): FindModel {
    val findManager = FindManager.getInstance(project)
    val findModel = FindModel()
    findModel.copyFrom(findManager.findInFileModel)
    return findModel
  }
}
