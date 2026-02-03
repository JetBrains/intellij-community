import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

class FakeFile(val pf: Project) {
  val pe: PsiElement? = null
  val r: PsiReference? = null
  var p: Project

  init {
    p = pf
  }
}