import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

class MyQuickFix implements QuickFix {

  @Override
  public String getName() {
    return "that fix do some fix";
  }

  @Override
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
    // any
  }

}
