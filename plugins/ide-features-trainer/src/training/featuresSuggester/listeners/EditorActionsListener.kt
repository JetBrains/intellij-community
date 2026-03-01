package training.featuresSuggester.listeners

import com.intellij.codeInsight.completion.actions.CodeCompletionAction
import com.intellij.codeInsight.lookup.impl.actions.ChooseItemAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.BackspaceAction
import com.intellij.openapi.editor.actions.CopyAction
import com.intellij.openapi.editor.actions.CutAction
import com.intellij.openapi.editor.actions.EscapeAction
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import training.featuresSuggester.SuggestingUtils
import training.featuresSuggester.SuggestingUtils.asString
import training.featuresSuggester.SuggestingUtils.getSelection
import training.featuresSuggester.SuggestingUtils.handleAction
import training.featuresSuggester.actions.BeforeCompletionChooseItemAction
import training.featuresSuggester.actions.BeforeEditorBackspaceAction
import training.featuresSuggester.actions.BeforeEditorCodeCompletionAction
import training.featuresSuggester.actions.BeforeEditorCopyAction
import training.featuresSuggester.actions.BeforeEditorCutAction
import training.featuresSuggester.actions.BeforeEditorEscapeAction
import training.featuresSuggester.actions.BeforeEditorFindAction
import training.featuresSuggester.actions.BeforeEditorPasteAction
import training.featuresSuggester.actions.CompletionChooseItemAction
import training.featuresSuggester.actions.EditorBackspaceAction
import training.featuresSuggester.actions.EditorCodeCompletionAction
import training.featuresSuggester.actions.EditorCopyAction
import training.featuresSuggester.actions.EditorCutAction
import training.featuresSuggester.actions.EditorEscapeAction
import training.featuresSuggester.actions.EditorFindAction
import training.featuresSuggester.actions.EditorPasteAction
import training.featuresSuggester.settings.FeatureSuggesterSettings

class EditorActionsListener : AnActionListener {
  override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
    FeatureSuggesterSettings.instance().updateWorkingDays()
    if (!action.isSupportedAction()) return
    val (project, editor, psiFile) = getCachedEventData(event)
    if (project == null || editor == null) return
    if (!SuggestingUtils.isActionsProcessingEnabled(project)) return
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
    if (!action.isSupportedAction()) return
    val (project, editor, psiFile) = getCachedEventData(event)
    if (project == null || editor == null) return
    if (!SuggestingUtils.isActionsProcessingEnabled(project)) return
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

  private fun getCachedEventData(event: AnActionEvent): Triple<Project?, Editor?, PsiFile?> {
    val context = when {
      ApplicationManager.getApplication().isUnitTestMode -> event.dataContext
      else -> Utils.getCachedOnlyDataContext(event.dataContext)
    }
    return context.let {
      Triple(
        CommonDataKeys.PROJECT.getData(it),
        CommonDataKeys.EDITOR.getData(it),
        CommonDataKeys.PSI_FILE.getData(it)
      )
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
