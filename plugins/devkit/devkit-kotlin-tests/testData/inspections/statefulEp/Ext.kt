import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

class Ext {
  val <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead">pe</warning>: PsiElement?
  val <warning descr="Do not use PsiReference as a field in extension">r</warning>: PsiReference?
  var <warning descr="Do not use Project as a field in extension">p</warning>: Project?
  val pf: Project?

  init {
    pe = null
    r = null
    pf = null
    p = pf
  }
}
