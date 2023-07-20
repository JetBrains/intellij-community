import com.intellij.codeInspection.LocalQuickFix
import kotlin.collections.Map
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

class MapTests : LocalQuickFix {
  val <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">pe</warning>: Map<PsiElement, PsiElement>? = null
  val <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">r</warning>: Map<PsiReference, PsiElement>? = null
  val <warning descr="Do not use PsiReference as a field in quick-fix">r1</warning>: Map<Map<PsiReference, PsiReference>, PsiReference>? = null
  var r2: Map<*, *>? = null
  var r3: Map<Int, Long>? = null
  val <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">r4</warning>: Collection<Map<PsiElement, PsiElement>>? = null
  val <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">r7</warning>: Map<PsiElement, Long>? = null
  val <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">r8</warning>: Map<Int, PsiElement>? = null
}
