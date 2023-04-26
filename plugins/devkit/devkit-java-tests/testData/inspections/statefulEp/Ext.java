import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.openapi.project.Project;

public class Ext {
  final PsiElement <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead">pe</warning>;
  final PsiReference <warning descr="Do not use PsiReference as a field in extension">r</warning>;
  Project <warning descr="Do not use Project as a field in extension">p</warning>;
  final Project pf;
  public Ext() {
    super();
    pe = null;
    r =null;
    p = pf = null;
  }
}
