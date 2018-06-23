public class ProjectComp implements com.intellij.openapi.components.ProjectComponent {
  <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead">final com.intellij.psi.PsiElement pe;</warning>
  <warning descr="Don't use PsiReference as a field in extension">final com.intellij.psi.PsiReference r;</warning>
  com.intellij.openapi.project.Project p;
  final com.intellij.openapi.project.Project pf;
  public ProjectComp() {
    super();
    pe = null;
    r =null;
    p = pf = null;
  }
}