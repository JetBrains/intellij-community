import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.QuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

class ViolationByPsiElementFieldUsage : QuickFix<ProblemDescriptor> {

  private var someField: String? = null
  private var myElement: PsiElement? = null

  override fun getName(): String {
    return someField!!
  }

  override fun <warning descr="QuickFix's getFamilyName() implementation must not depend on a specific context">getFamilyName</warning>(): String {
    return "error is here: " + myElement.toString()
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    // any
  }
}
