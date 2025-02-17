package ru.adelf.idea.dotenv.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import ru.adelf.idea.dotenv.psi.DotEnvFile

class NestedVariableTypedHandlerDelegate: TypedHandlerDelegate() {

    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is DotEnvFile) {
            return super.checkAutoPopup(charTyped, project, editor, file)
        }
        return when(charTyped) {
            '$' -> {
                AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null)
                return Result.CONTINUE
            }
            else -> super.checkAutoPopup(charTyped, project, editor, file)
        }
    }

}