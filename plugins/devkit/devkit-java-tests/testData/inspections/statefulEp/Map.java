public class Map extends com.intellij.codeInspection.LocalQuickFix {
  java.util.Map<com.intellij.psi.PsiElement, com.intellij.psi.PsiElement> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead See also LocalQuickFixOnPsiElement">pe</warning>;
  java.util.Map<com.intellij.psi.PsiReference, com.intellij.psi.PsiElement> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead See also LocalQuickFixOnPsiElement">r</warning>;
  java.util.Map<java.util.Map<com.intellij.psi.PsiReference, com.intellij.psi.PsiReference>, com.intellij.psi.PsiReference> <warning descr="Do not use PsiReference as a field in quick-fix">r1</warning>;
  java.util.Map r2;
  java.util.Map<Integer, Long> r3;
  java.util.Collection<java.util.Map<com.intellij.psi.PsiElement, com.intellij.psi.PsiElement>> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead See also LocalQuickFixOnPsiElement">r4</warning>;
  java.util.Map<com.intellij.psi.PsiElement, Long> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead See also LocalQuickFixOnPsiElement">r7</warning>;
  java.util.Map<Integer, com.intellij.psi.PsiElement> <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead See also LocalQuickFixOnPsiElement">r8</warning>;
}