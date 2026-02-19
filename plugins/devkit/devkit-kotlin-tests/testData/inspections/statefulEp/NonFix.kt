import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

class NonFix {
  val pe: PsiElement? = null
  val r: PsiReference? = null
  var p: Project?
  val pf: Project? = null

  init {
    p = pf
  }

  class Ext2
}
