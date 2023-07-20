import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

class CapturedFromOuterClass(a: PsiElement?, b: String?) : LocalQuickFix {
  val <warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">pe</warning>: PsiElement?
  val <warning descr="Do not use PsiReference as a field in quick-fix">r</warning>: PsiReference?
  var <warning descr="Do not use Project as a field in quick-fix">p</warning>: Project?
  val pf: Project?

  init {
    pe = null
    r = null
    pf = null
    p = pf

    @Suppress("UNUSED_VARIABLE")
    val fix: LocalQuickFix = object : LocalQuickFix {
      private fun a(a1: PsiElement, b1: String) {
        any(<warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">a</warning>)
        any(b)
        any(a1)
        any(b1)
      }
    }

    @Suppress("UNUSED_VARIABLE")
    val notFix: Any = object : Any() {
      private fun a(a1: PsiElement, b1: String) {
        any(a)
        any(b)
        any(a1)
        any(b1)
      }
    }
  }

  fun test(a: PsiElement?, b: String?) {
    open class B(@Suppress("UNUSED_PARAMETER") aa: PsiElement?) : LocalQuickFix {
      private fun a(a1: PsiElement, b1: String) {
        any(<warning descr="Potential memory leak: don't hold PsiElement, use SmartPsiElementPointer instead. See also LocalQuickFixOnPsiElement.">a</warning>)
        any(b)
        any(a1)
        any(b1)
      }
    }

    @Suppress("UNUSED_VARIABLE")
    val b1 = object : B(a) {}
  }
}

private fun any(@Suppress("UNUSED_PARAMETER") any: Any?) {
  // any
}
