public class Ref extends com.intellij.codeInspection.LocalQuickFix {
  com.intellij.openapi.util.Ref<com.intellij.psi.PsiElement> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead See also LocalQuickFixOnPsiElement">pe</warning>;
  com.intellij.openapi.util.Ref<com.intellij.psi.PsiReference> <warning descr="Do not use PsiReference as a field in quick fix">r</warning>;
  com.intellij.openapi.util.Ref<com.intellij.openapi.util.Ref<com.intellij.psi.PsiReference>> <warning descr="Do not use PsiReference as a field in quick fix">r1</warning>;
  com.intellij.openapi.util.Ref r2;
  com.intellij.openapi.util.Ref<Integer> r3;
  com.intellij.openapi.util.Ref<com.intellij.openapi.util.Ref<com.intellij.psi.PsiElement>> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead See also LocalQuickFixOnPsiElement">r4</warning>;
  com.intellij.openapi.project.Project <warning descr="Do not use Project as a field in quick fix">p</warning>;
}