import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import java.util.Collection;
import java.util.Map;

public class MapTests implements LocalQuickFix {
  Map<PsiElement, PsiElement> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">pe</warning>;
  Map<PsiReference, PsiElement> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">r</warning>;
  Map<Map<PsiReference, PsiReference>, PsiReference> <warning descr="Do not use PsiReference as a field in quick-fix">r1</warning>;
  Map r2;
  Map<Integer, Long> r3;
  Collection<Map<PsiElement, PsiElement>> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">r4</warning>;
  Map<PsiElement, Long> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">r7</warning>;
  Map<Integer, PsiElement> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">r8</warning>;
}
