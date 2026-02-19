import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.QuickFix
import com.intellij.openapi.project.Project

class NotViolatedByExternalParameter {
  fun getFix(someParameter: String): QuickFix<ProblemDescriptor> {
    return object : QuickFix<ProblemDescriptor> {
      override fun getName(): String {
        return "some name"
      }

      override fun getFamilyName(): String {
        return someParameter + "123"
      }

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // any
      }
    }
  }
}
