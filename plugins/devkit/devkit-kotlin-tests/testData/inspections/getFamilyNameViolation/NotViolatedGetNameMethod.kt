import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.QuickFix
import com.intellij.openapi.project.Project

class NotViolatedGetNameMethod : QuickFix<ProblemDescriptor> {
  override fun getName(): String {
    return "that fix do some fix"
  }

  override fun getFamilyName(): String {
    return name
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    // any
  }
}