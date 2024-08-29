package training.featuresSuggester.suggesters

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.vfs.VirtualFile
import training.featuresSuggester.FeatureSuggesterBundle
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.SuggestingUtils.asString
import training.featuresSuggester.Suggestion
import training.featuresSuggester.actions.Action
import training.featuresSuggester.actions.EditorCopyAction
import training.featuresSuggester.actions.InlineEvaluatorInvokedAction
import java.lang.ref.WeakReference

class QuickEvaluateSuggester : AbstractFeatureSuggester() {
  override val id: String = "QuickEvaluateSuggester"
  override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("quick.evaluate.name")

  override val suggestingActionId = "QuickEvaluateExpression"
  override val suggestingDocUrl = "https://www.jetbrains.com/help/idea/examining-suspended-program.html#quick-evaluate"
  override val minSuggestingIntervalDays = 14

  override val languages = listOf(Language.ANY.id)

  override val forceCheckForStatistics = true

  private var lastCopyFromFile = WeakReference<VirtualFile>(null)
  private var lastCopiedText = ""

  override fun isSuggestionNeeded(): Boolean {
    return super.isSuggestionNeeded() && getMouseShortcut() != null
  }

  override val message: String get() {
    val modifiersText = getMouseShortcut()?.modifiers?.let { KeymapUtil.getModifiersText(it) } ?: ""
    return FeatureSuggesterBundle.message("quick.evaluate.message", modifiersText)
  }

  override fun getSuggestion(action: Action): Suggestion {
    when (action) {
      is InlineEvaluatorInvokedAction -> {
        val clipboardContent = CopyPasteManager.getInstance().allContents.firstOrNull()?.asString()
        if (clipboardContent != lastCopiedText) return NoSuggestion
        if (action.expression.expression != lastCopiedText) return NoSuggestion
        val neededVirtualFile = lastCopyFromFile.get() ?: return NoSuggestion

        val fileIsOpenedNow = FileEditorManager.getInstance(action.project).selectedEditors.map { it.file }.contains(neededVirtualFile)
        if (!fileIsOpenedNow) return NoSuggestion

        return createSuggestion()
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
    getMouseShortcut()?.let {
      return KeymapUtil.getMouseShortcutText(it)
    }
    return super.getShortcutText(actionId)
  }

  private fun getMouseShortcut() =
    ActionManager.getInstance().getAction(suggestingActionId)?.shortcutSet?.shortcuts?.filterIsInstance<MouseShortcut>()?.firstOrNull()
}
