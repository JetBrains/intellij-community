package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class RegisteredProjectService {
  public static RegisteredProjectService getInstance() {
    return ApplicationManager.getApplication().getService(RegisteredProjectService.class);
  }
}
