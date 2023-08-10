import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

public class ProjectComp implements ProjectComponent {
  final PsiElement <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead">pe</warning>;
  final PsiReference <warning descr="Do not use PsiReference as a field in extension">r</warning>;
  Project p;
  final Project pf;
  public ProjectComp() {
    super();
    pe = null;
    r = null;
    p = pf = null;
  }
}
