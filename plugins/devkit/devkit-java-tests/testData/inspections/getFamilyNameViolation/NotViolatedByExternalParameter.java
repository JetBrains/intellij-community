import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

class A {

  QuickFix getFix(final String someParameter) {
    return new QuickFix() {

      @Override
      public String getName() {
        return "some name";
      }

      @Override
      public String getFamilyName() {
        return someParameter + "123";
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
        // any
      }
    };
  }

}
