import com.intellij.psi.PsiElement;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiReference;
import com.intellij.openapi.project.Project;

public class CapturedFromOuterClass implements LocalQuickFix {
  final PsiElement <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">pe</warning>;
  final PsiReference <warning descr="Do not use PsiReference as a field in quick-fix">r</warning>;
  Project <warning descr="Do not use Project as a field in quick-fix">p</warning>;
  final Project pf;
  public CapturedFromOuterClass(PsiElement a, String b) {
    pe = null;
    r = null;
    p = pf = null;

    LocalQuickFix fix = new LocalQuickFix() {
      private void a(PsiElement a1, String b1) {
        System.out.println(<warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">a</warning>);
        System.out.println(b);
        System.out.println(a1);
        System.out.println(b1);
      }
    };
    PsiElement notFix = new PsiElement() {
      private void a(PsiElement a1, String b1) {
        System.out.println(a);
        System.out.println(b);
        System.out.println(a1);
        System.out.println(b1);
      }
    };
  }

  public void test(PsiElement a, String b) {
    class B implements LocalQuickFix {
      B(PsiElement aa) {
      }

      private void a(PsiElement a1, String b1) {
        System.out.println(<warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">a</warning>);
        System.out.println(b);
        System.out.println(a1);
        System.out.println(b1);
      }
    };
    B b1 = new B(a) {};
  }
}
