import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.QuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

abstract class ViolationByPsiElementFieldUsageInUsedParentClassMethod : QuickFix<ProblemDescriptor> {

  private var someField: String? = null
  private var myElement: PsiElement? = null

  override fun getName(): String {
    return someField!!
  }

  protected fun getValueOfMyElement() = myElement.toString() // violation

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    // any
  }
}

class MyQuickFix : ViolationByPsiElementFieldUsageInUsedParentClassMethod() {
  override fun <warning descr="QuickFix's getFamilyName() implementation must not depend on a specific context">getFamilyName</warning>(): String {
    return "error is here: " + getValueOfMyElement()
  }
}
