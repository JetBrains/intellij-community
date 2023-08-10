import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

class MyQuickFix implements QuickFix {

  String someField;
  PsiElement myElement;

  @Override
  public String getName() {
    return someField;
  }

  @Override
  public String <warning descr="QuickFix's getFamilyName() implementation must not depend on a specific context">getFamilyName</warning>() {
    return "error is here: " + getValueOfMyElement();
  }

  private String getValueOfMyElement() {
    return String.valueOf(myElement); // violation
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
    // any
  }
}
