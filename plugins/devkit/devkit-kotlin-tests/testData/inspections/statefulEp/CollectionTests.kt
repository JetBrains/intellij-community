import com.intellij.openapi.project.Project
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

class CollectionTests : LocalQuickFix {
  var <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">pe</warning>: Collection<PsiElement>? = null
  var <warning descr="Do not use PsiReference as a field in quick-fix">r</warning>: Collection<PsiReference>? = null
  var <warning descr="Do not use PsiReference as a field in quick-fix">r1</warning>: Collection<Collection<PsiReference>>? = null
  var r2: Collection<*>? = null
  var r3: Collection<Int>? = null
  var <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">r4</warning>: Collection<Collection<PsiElement>>? = null
  var <warning descr="Do not use Project as a field in quick-fix">p</warning>: Project? = null
}
