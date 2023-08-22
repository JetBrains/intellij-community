import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.openapi.project.Project

class RefTests : LocalQuickFix {
  var <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">pe</warning>: Ref<PsiElement>? = null
  var <warning descr="Do not use PsiReference as a field in quick-fix">r</warning>: Ref<PsiReference>? = null
  var <warning descr="Do not use PsiReference as a field in quick-fix">r1</warning>: Ref<Ref<PsiReference>>? = null
  var r2: Ref<*>? = null
  var r3: Ref<Int>? = null
  var <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">r4</warning>: Ref<Ref<PsiElement>>? = null
  var <warning descr="Do not use Project as a field in quick-fix">p</warning>: Project? = null
}
