public class Fix extends com.intellij.codeInspection.LocalQuickFix {
  final com.intellij.psi.PsiElement <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead; also see LocalQuickFixOnPsiElement">pe</warning>;
  final com.intellij.psi.PsiReference <warning descr="Don't use PsiReference as a field in quick fix">r</warning>;
  com.intellij.openapi.project.Project <warning descr="Don't use Project as a field in quick fix">p</warning>;
  final com.intellij.openapi.project.Project pf;
  public Fix() {
    super();
    pe = null;
    r =null;
    p = pf = null;
  }
}