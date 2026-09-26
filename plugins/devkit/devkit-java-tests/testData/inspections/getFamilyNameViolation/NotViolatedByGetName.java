import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

class MyQuickFix implements QuickFix {

  String someField;

  @Override
  public String getName() {
    return someField;
  }

  @Override
  public String getFamilyName() {
    return getName() + "123";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
    // any
  }

}
