import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

public class MyJavaErrorFix implements com.intellij.codeInsight.intention.IntentionAction {

  public String getText() { return"text"; }
  public String getFamilyName() { return"familyName"; }
  public boolean isAvailable(Project project,Editor editor,PsiFile file) {return true; }
  public void invoke(Project project,Editor editor,PsiFile file) throws IncorrectOperationException {}
  public boolean startInWriteAction() { return true; }

}