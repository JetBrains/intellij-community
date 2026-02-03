import com.intellij.openapi.util.Ref;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.openapi.project.Project;

public class RefTests implements LocalQuickFix {
  Ref<PsiElement> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">pe</warning>;
  Ref<PsiReference> <warning descr="Do not use PsiReference as a field in quick-fix">r</warning>;
  Ref<Ref<PsiReference>> <warning descr="Do not use PsiReference as a field in quick-fix">r1</warning>;
  Ref r2;
  Ref<Integer> r3;
  Ref<Ref<PsiElement>> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">r4</warning>;
  Project <warning descr="Do not use Project as a field in quick-fix">p</warning>;
}
