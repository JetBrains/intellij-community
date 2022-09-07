public class Collection extends com.intellij.codeInspection.LocalQuickFix {
  java.util.Collection<com.intellij.psi.PsiElement> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead See also LocalQuickFixOnPsiElement">pe</warning>;
  java.util.Collection<com.intellij.psi.PsiReference> <warning descr="Do not use PsiReference as a field in quick fix">r</warning>;
  java.util.Collection<java.util.Collection<com.intellij.psi.PsiReference>> <warning descr="Do not use PsiReference as a field in quick fix">r1</warning>;
  java.util.Collection r2;
  java.util.Collection<Integer> r3;
  java.util.Collection<java.util.Collection<com.intellij.psi.PsiElement>> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead See also LocalQuickFixOnPsiElement">r4</warning>;
  com.intellij.openapi.project.Project <warning descr="Do not use Project as a field in quick fix">p</warning>;
}