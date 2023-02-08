import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.QuickFix
import com.intellij.openapi.project.Project

class NotViolatedByField : QuickFix<ProblemDescriptor> {
  private var someField: String? = null

  override fun getName(): String {
    return "some name"
  }

  override fun getFamilyName(): String {
    return someField + name + "123"
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    // any
  }
}
