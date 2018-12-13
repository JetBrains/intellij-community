public class ProjectComp implements com.intellij.openapi.components.ProjectComponent {
  final com.intellij.psi.PsiElement <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead">pe</warning>;
  final com.intellij.psi.PsiReference <warning descr="Don't use PsiReference as a field in extension">r</warning>;
  com.intellij.openapi.project.Project p;
  final com.intellij.openapi.project.Project pf;
  public ProjectComp() {
    super();
    pe = null;
    r =null;
    p = pf = null;
  }
}