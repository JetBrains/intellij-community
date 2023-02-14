import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.QuickFix
import com.intellij.openapi.project.Project

class NotViolatedByGetName : QuickFix<ProblemDescriptor> {
  private var someField: String? = null

  override fun getName(): String {
    return someField!!
  }

  override fun getFamilyName(): String {
    return name + "123"
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    // any
  }
}
