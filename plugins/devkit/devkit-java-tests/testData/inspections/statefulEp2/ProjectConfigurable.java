import com.intellij.openapi.project.Project;

public class ProjectConfigurable {
  final com.intellij.psi.PsiElement <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead">pe</warning>;
  final com.intellij.psi.PsiReference <warning descr="Don't use PsiReference as a field in extension">r</warning>;
  Project p;
  final Project pf;
  public ProjectConfigurable(Project project) {
    super();
    pe = null;
    r = null;
    p = pf = project;
  }
}