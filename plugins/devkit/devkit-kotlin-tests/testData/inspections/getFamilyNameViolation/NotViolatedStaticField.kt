import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.QuickFix
import com.intellij.openapi.project.Project

class NotViolatedStaticField : QuickFix<ProblemDescriptor> {

  companion object {
    var someField = "Hello"
  }

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
