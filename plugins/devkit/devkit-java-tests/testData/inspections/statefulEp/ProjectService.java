import com.intellij.openapi.project.Project;

public class ProjectService {
  <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead">final com.intellij.psi.PsiElement pe;</warning>
  <warning descr="Don't use PsiReference as a field in extension">final com.intellij.psi.PsiReference r;</warning>
  Project p;
  final Project pf;
  public ProjectService(Project project) {
    super();
    pe = null;
    r = null;
    p = pf = project;
  }
}