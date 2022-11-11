public class CapturedFromOuterClass extends com.intellij.codeInspection.LocalQuickFix {
  final com.intellij.psi.PsiElement <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead See also LocalQuickFixOnPsiElement">pe</warning>;
  final com.intellij.psi.PsiReference <warning descr="Do not use PsiReference as a field in quick fix">r</warning>;
  com.intellij.openapi.project.Project <warning descr="Do not use Project as a field in quick fix">p</warning>;
  final com.intellij.openapi.project.Project pf;
  public CapturedFromOuterClass(com.intellij.psi.PsiElement a, String b) {
    super();
    pe = null;
    r =null;
    p = pf = null;

    com.intellij.codeInspection.LocalQuickFix fix = new com.intellij.codeInspection.LocalQuickFix() {
      private void a(com.intellij.psi.PsiElement a1, String b1) {
        System.out.println(<warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead See also LocalQuickFixOnPsiElement">a</warning>);
        System.out.println(b);
        System.out.println(a1);
        System.out.println(b1);
      }
    };
    com.intellij.psi.PsiElement notFix = new com.intellij.psi.PsiElement() {
      private void a(com.intellij.psi.PsiElement a1, String b1) {
        System.out.println(a);
        System.out.println(b);
        System.out.println(a1);
        System.out.println(b1);
      }
    };
  }

  public void test(com.intellij.psi.PsiElement a, String b) {
    class B extends com.intellij.codeInspection.LocalQuickFix {
      B(com.intellij.psi.PsiElement aa) {
      }

      private void a(com.intellij.psi.PsiElement a1, String b1) {
        System.out.println(<warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead See also LocalQuickFixOnPsiElement">a</warning>);
        System.out.println(b);
        System.out.println(a1);
        System.out.println(b1);
      }
    };
    B b1 = new B(a) {};
  }
}