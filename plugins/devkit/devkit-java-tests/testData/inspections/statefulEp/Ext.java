public class Ext {
  final com.intellij.psi.PsiElement <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead">pe</warning>;
  final com.intellij.psi.PsiReference <warning descr="Don't use PsiReference as a field in extension">r</warning>;
  com.intellij.openapi.project.Project <warning descr="Don't use Project as a field in extension">p</warning>;
  final com.intellij.openapi.project.Project pf;
  public Ext() {
    super();
    pe = null;
    r =null;
    p = pf = null;
  }
}