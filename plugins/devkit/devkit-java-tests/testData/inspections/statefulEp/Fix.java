import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.openapi.project.Project;

public class Fix implements LocalQuickFix {
  final PsiElement <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">pe</warning>;
  final PsiReference <warning descr="Do not use PsiReference as a field in quick-fix">r</warning>;
  Project <warning descr="Do not use Project as a field in quick-fix">p</warning>;
  final Project pf;
  public Fix() {
    super();
    pe = null;
    r =null;
    p = pf = null;
  }
}
