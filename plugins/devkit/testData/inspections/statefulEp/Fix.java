public class Fix extends com.intellij.codeInspection.LocalQuickFix {
  <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead; also see LocalQuickFixOnPsiElement">final com.intellij.psi.PsiElement pe;</warning>
  <warning descr="Don't use PsiReference as a field in extension">final com.intellij.psi.PsiReference r;</warning>
  <warning descr="Don't use Project as a field in extension">com.intellij.openapi.project.Project p;</warning>
  final com.intellij.openapi.project.Project pf;
  public Fix() {
    super();
    pe = null;
    r =null;
    p = pf = null;
  }
}