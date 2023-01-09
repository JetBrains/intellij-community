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
    return getNameStatic() + "123";
  }

  static String getNameStatic() {
    return "Static";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
    // any
  }

}
