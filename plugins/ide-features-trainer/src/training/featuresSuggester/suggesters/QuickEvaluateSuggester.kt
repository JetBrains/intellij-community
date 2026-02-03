package training.featuresSuggester.suggesters

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.vfs.VirtualFile
import training.featuresSuggester.CustomSuggestion
import training.featuresSuggester.FeatureSuggesterBundle
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.SuggestingUtils.asString
import training.featuresSuggester.Suggestion
import training.featuresSuggester.actions.Action
import training.featuresSuggester.actions.EditorCopyAction
import training.featuresSuggester.actions.InlineEvaluatorInvokedAction
import training.featuresSuggester.statistics.AltClickSuggesterResult
import training.featuresSuggester.statistics.FeatureSuggesterStatistics
import training.featuresSuggester.suggesters.promo.evaluateBox
import training.featuresSuggester.suggesters.promo.showAltClickGotItPromo
import java.awt.event.InputEvent
import java.lang.ref.WeakReference

class QuickEvaluateSuggester : AbstractFeatureSuggester() {
  override val id: String = "QuickEvaluateSuggester"
  override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("quick.evaluate.name")

  override val suggestingActionId = "QuickEvaluateExpression"
  override val suggestingDocUrl = "https://www.jetbrains.com/help/idea/examining-suspended-program.html#quick-evaluate"
  override val minSuggestingIntervalDays = 14

  override val languages = listOf(Language.ANY.id)

  override val forceCheckForStatistics = true

  override val enabledByDefault = true

  private var lastCopyFromFile = WeakReference<VirtualFile>(null)
  private var lastCopiedText = ""

  override fun isSuggestionNeeded(): Boolean {
    return super.isSuggestionNeeded() && getAltClickShortcut() != null
  }

  override val message: String get() {
    val modifiersText = getAltClickShortcut()?.modifiers?.let { KeymapUtil.getModifiersText(it) } ?: ""
    return FeatureSuggesterBundle.message("quick.evaluate.message", modifiersText)
  }

  override fun getSuggestion(action: Action): Suggestion {
    when (action) {
      is InlineEvaluatorInvokedAction -> {
        val clipboardContent = CopyPasteManager.getInstance().allContents.firstOrNull()?.asString()
        if (clipboardContent != lastCopiedText) return NoSuggestion
        if (action.expression.expression != lastCopiedText) return NoSuggestion
        val neededVirtualFile = lastCopyFromFile.get() ?: return NoSuggestion

        val language = (neededVirtualFile.fileType as? LanguageFileType)?.language ?: return NoSuggestion

        val fileIsOpenedNow = FileEditorManager.getInstance(action.project).selectedEditors.map { it.file }.contains(neededVirtualFile)
        if (!fileIsOpenedNow) {
          FeatureSuggesterStatistics.altClickSuggesterResult(AltClickSuggesterResult.FILE_IS_NOT_OPENED, language)
          return NoSuggestion
        }

        val evaluateBox = evaluateBox(action.project) ?: return NoSuggestion.also {
          FeatureSuggesterStatistics.altClickSuggesterResult(AltClickSuggesterResult.NO_EVALUATE_BOX_FOUND, language)
        }

        when {
          getAltClickShortcut() == null ->
            FeatureSuggesterStatistics.altClickSuggesterResult(AltClickSuggesterResult.NO_ALT_CLICK_SHORTCUT, language)
          isSuggestingActionUsedRecently() ->
            FeatureSuggesterStatistics.altClickSuggesterResult(AltClickSuggesterResult.QUICK_EVALUATE_ACTION_USED_RECENTLY, language)
          isSuggestionShownRecently() ->
            FeatureSuggesterStatistics.altClickSuggesterResult(AltClickSuggesterResult.SUGGESTER_WAS_SHOWN_RECENTLY, language)
        }

        return CustomSuggestion(id) {
          FeatureSuggesterStatistics.altClickSuggesterResult(AltClickSuggesterResult.SUGGESTED, language)
          showAltClickGotItPromo(action.project, evaluateBox)
        }
      }
      is EditorCopyAction -> {
        val virtualFile = action.editor.virtualFile ?: return NoSuggestion
        lastCopyFromFile = WeakReference<VirtualFile>(virtualFile)
        lastCopiedText = action.text
      }
    }
    return NoSuggestion
  }

  override fun getShortcutText(actionId: String): String {
    getAltClickShortcut()?.let {
      return KeymapUtil.getMouseShortcutText(it)
    }
    return super.getShortcutText(actionId)
  }

  private fun getAltClickShortcut(): MouseShortcut? {
    val allMouseShortcuts = allMouseShortcuts()
    return allMouseShortcuts.singleOrNull {
      it.button == 1 && it.clickCount == 1 && isJustAlt(it.modifiers)
    }
  }

  private fun allMouseShortcuts(): List<MouseShortcut> =
    ActionManager.getInstance().getAction(suggestingActionId)?.shortcutSet?.shortcuts?.filterIsInstance<MouseShortcut>() ?: emptyList()

}

private fun isJustAlt(modifiers: Int): Boolean {
  val oldModifiers = ((modifiers and (InputEvent.META_MASK or InputEvent.CTRL_MASK or InputEvent.SHIFT_MASK)) == 0) &&
             ((modifiers and InputEvent.ALT_MASK) != 0)
  val newModifiers = ((modifiers and (InputEvent.META_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)) == 0) &&
                     ((modifiers and InputEvent.ALT_DOWN_MASK) != 0)
  return oldModifiers || newModifiers
}