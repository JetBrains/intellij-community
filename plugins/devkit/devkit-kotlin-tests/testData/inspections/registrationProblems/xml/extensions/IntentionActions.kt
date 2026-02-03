import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

object SingletonIntentionAction : IntentionAction {
  override fun startInWriteAction() = true
  override fun getText() = "any"
  override fun getFamilyName() = "any"
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    // any
  }
}

class MyIntentionAction : IntentionAction {
  override fun startInWriteAction() = true
  override fun getText() = "any"
  override fun getFamilyName() = "any"
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    // any
  }
}
