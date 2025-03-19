// MOVE: up
fun dispose(editor: Editor) {
    val disposable = editor.getUserData(AIDiffKeys.AI_DIFF_VIEWER) as AIInEditorDiffViewer?
    if (disposable != null) {

    }
    Disposer.dispose(disposable))<caret>
}