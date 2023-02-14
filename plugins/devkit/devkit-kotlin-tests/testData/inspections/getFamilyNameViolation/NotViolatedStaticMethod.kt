import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.QuickFix
import com.intellij.openapi.project.Project

class NotViolatedStaticMethod : QuickFix<ProblemDescriptor> {

  companion object {
    val nameStatic = "Static"
  }

  var someField: String? = null

  override fun getName(): String {
    return someField!!
  }

  override fun getFamilyName(): String {
    return nameStatic + "123"
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    // any
  }
}