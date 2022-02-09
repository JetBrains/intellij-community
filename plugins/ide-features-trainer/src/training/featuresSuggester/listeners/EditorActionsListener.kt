package training.featuresSuggester.listeners

import com.intellij.codeInsight.completion.actions.CodeCompletionAction
import com.intellij.codeInsight.lookup.impl.actions.ChooseItemAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.*
import com.intellij.openapi.ide.CopyPasteManager
import training.featuresSuggester.actions.*
import training.featuresSuggester.asString
import training.featuresSuggester.getSelection
import training.featuresSuggester.handleAction
import training.featuresSuggester.isActionsProcessingEnabled
import training.featuresSuggester.settings.FeatureSuggesterSettings

class EditorActionsListener : AnActionListener {
  override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
    FeatureSuggesterSettings.instance().updateWorkingDays()
    if (!isActionsProcessingEnabled || !action.isSupportedAction()) return
    val editor = event.getData(CommonDataKeys.EDITOR) ?: return
    val project = event.getData(CommonDataKeys.PROJECT) ?: return
    val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return
    when (action) {
      is CopyAction -> {
        val copiedText = CopyPasteManager.getInstance().contents?.asString() ?: return
        handleAction(
          project,
          EditorCopyAction(
            text = copiedText,
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is CutAction -> {
        val text = CopyPasteManager.getInstance().contents?.asString() ?: return
        handleAction(
          project,
          EditorCutAction(
            text = text,
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is PasteAction -> {
        val pastedText = CopyPasteManager.getInstance().contents?.asString() ?: return
        val caretOffset = editor.getCaretOffset()
        handleAction(
          project,
          EditorPasteAction(
            text = pastedText,
            caretOffset = caretOffset,
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is BackspaceAction -> {
        handleAction(
          project,
          EditorBackspaceAction(
            textFragment = editor.getSelection(),
            caretOffset = editor.getCaretOffset(),
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is IncrementalFindAction -> {
        handleAction(
          project,
          EditorFindAction(
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is CodeCompletionAction -> {
        handleAction(
          project,
          EditorCodeCompletionAction(
            caretOffset = editor.caretModel.offset,
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is ChooseItemAction.FocusedOnly -> {
        handleAction(
          project,
          CompletionChooseItemAction(
            caretOffset = editor.caretModel.offset,
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is EscapeAction -> {
        handleAction(
          project,
          EditorEscapeAction(
            caretOffset = editor.caretModel.offset,
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
    }
  }

  override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
    if (!isActionsProcessingEnabled || !action.isSupportedAction()) return
    val editor = event.getData(CommonDataKeys.EDITOR) ?: return
    val project = event.getData(CommonDataKeys.PROJECT) ?: return
    val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return
    when (action) {
      is CopyAction -> {
        val selectedText = editor.getSelectedText() ?: return
        handleAction(
          project,
          BeforeEditorCopyAction(
            text = selectedText,
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is CutAction -> {
        handleAction(
          project,
          BeforeEditorCutAction(
            textFragment = editor.getSelection(),
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is PasteAction -> {
        val pastedText = CopyPasteManager.getInstance().contents?.asString() ?: return
        val caretOffset = editor.getCaretOffset()
        handleAction(
          project,
          BeforeEditorPasteAction(
            text = pastedText,
            caretOffset = caretOffset,
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is BackspaceAction -> {
        handleAction(
          project,
          BeforeEditorBackspaceAction(
            textFragment = editor.getSelection(),
            caretOffset = editor.getCaretOffset(),
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is IncrementalFindAction -> {
        handleAction(
          project,
          BeforeEditorFindAction(
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is CodeCompletionAction -> {
        handleAction(
          project,
          BeforeEditorCodeCompletionAction(
            caretOffset = editor.caretModel.offset,
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is ChooseItemAction.FocusedOnly -> {
        handleAction(
          project,
          BeforeCompletionChooseItemAction(
            caretOffset = editor.caretModel.offset,
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
      is EscapeAction -> {
        handleAction(
          project,
          BeforeEditorEscapeAction(
            caretOffset = editor.caretModel.offset,
            editor = editor,
            psiFile = psiFile,
            timeMillis = System.currentTimeMillis()
          )
        )
      }
    }
  }

  private fun Editor.getSelectedText(): String? {
    return selectionModel.selectedText
  }

  private fun Editor.getCaretOffset(): Int {
    return caretModel.offset
  }

  private fun AnAction.isSupportedAction(): Boolean {
    return this is CopyAction || this is CutAction ||
           this is PasteAction || this is BackspaceAction ||
           this is IncrementalFindAction || this is CodeCompletionAction ||
           this is ChooseItemAction.FocusedOnly || this is EscapeAction
  }
}
