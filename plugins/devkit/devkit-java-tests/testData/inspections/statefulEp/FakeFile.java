import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

public class FakeFile {
  final PsiElement pe;
  final PsiReference r;
  Project p;
  final Project pf;

  public FakeFile(Project project) {
    super();
    pe = null;
    r = null;
    p = pf = project;
  }
}
