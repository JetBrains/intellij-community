package training.featuresSuggester.actions

import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import training.featuresSuggester.TextFragment

sealed class EditorAction : Action() {
  abstract val editor: Editor
  abstract val psiFile: PsiFile?

  override val language: Language?
    get() = psiFile?.language

  override val project: Project?
    get() = editor.project

  val document: Document
    get() = editor.document

  protected fun getPsiFileFromEditor(): PsiFile? {
    val project = editor.project ?: return null
    return PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
  }
}

// -------------------------------------EDITOR AFTER ACTIONS-------------------------------------
data class EditorBackspaceAction(
  val textFragment: TextFragment?,
  val caretOffset: Int,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class EditorCopyAction(
  val text: String,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class EditorCutAction(
  val text: String,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class EditorPasteAction(
  val text: String,
  val caretOffset: Int,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class EditorTextInsertedAction(
  val text: String,
  val caretOffset: Int,
  override val editor: Editor,
  override val timeMillis: Long
) : EditorAction() {
  override val psiFile: PsiFile?
    get() = getPsiFileFromEditor()
}

data class EditorTextRemovedAction(
  val textFragment: TextFragment,
  val caretOffset: Int,
  override val editor: Editor,
  override val timeMillis: Long
) : EditorAction() {
  override val psiFile: PsiFile?
    get() = getPsiFileFromEditor()
}

data class EditorFindAction(
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class EditorCodeCompletionAction(
  val caretOffset: Int,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class CompletionChooseItemAction(
  val caretOffset: Int,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class EditorEscapeAction(
  val caretOffset: Int,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class EditorFocusGainedAction(
  override val editor: Editor,
  override val timeMillis: Long
) : EditorAction() {
  override val psiFile: PsiFile?
    get() = getPsiFileFromEditor()
}

// -------------------------------------EDITOR BEFORE ACTIONS-------------------------------------
data class BeforeEditorBackspaceAction(
  val textFragment: TextFragment?,
  val caretOffset: Int,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class BeforeEditorCopyAction(
  val text: String,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class BeforeEditorCutAction(
  val textFragment: TextFragment?,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class BeforeEditorPasteAction(
  val text: String,
  val caretOffset: Int,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class BeforeEditorTextInsertedAction(
  val text: String,
  val caretOffset: Int,
  override val editor: Editor,
  override val timeMillis: Long
) : EditorAction() {
  override val psiFile: PsiFile?
    get() = getPsiFileFromEditor()
}

data class BeforeEditorTextRemovedAction(
  val textFragment: TextFragment,
  val caretOffset: Int,
  override val editor: Editor,
  override val timeMillis: Long
) : EditorAction() {
  override val psiFile: PsiFile?
    get() = getPsiFileFromEditor()
}

data class BeforeEditorFindAction(
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class BeforeEditorCodeCompletionAction(
  val caretOffset: Int,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class BeforeCompletionChooseItemAction(
  val caretOffset: Int,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()

data class BeforeEditorEscapeAction(
  val caretOffset: Int,
  override val editor: Editor,
  override val psiFile: PsiFile?,
  override val timeMillis: Long
) : EditorAction()
