import com.intellij.codeInspection.LocalQuickFix;
import java.util.Collection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.openapi.project.Project;

public class CollectionTests implements LocalQuickFix {
  Collection<PsiElement> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">pe</warning>;
  Collection<PsiReference> <warning descr="Do not use PsiReference as a field in quick-fix">r</warning>;
  Collection<Collection<PsiReference>> <warning descr="Do not use PsiReference as a field in quick-fix">r1</warning>;
  Collection r2;
  Collection<Integer> r3;
  Collection<Collection<PsiElement>> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">r4</warning>;
  Project <warning descr="Do not use Project as a field in quick-fix">p</warning>;
}
