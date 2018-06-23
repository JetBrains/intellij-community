import com.intellij.openapi.project.Project;

public class FakeFile {
  final com.intellij.psi.PsiElement pe;
  final com.intellij.psi.PsiReference r;
  Project p;
  final Project pf;

  public FakeFile(Project project) {
    super();
    pe = null;
    r = null;
    p = pf = project;
  }
}