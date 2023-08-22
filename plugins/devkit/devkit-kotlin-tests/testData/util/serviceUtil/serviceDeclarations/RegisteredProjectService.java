package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

/**
 * PROJECT
 */
public class RegisteredProjectService {
  public static RegisteredProjectService getInstance(Project project) {
    return project.getService(RegisteredProjectService.class);
  }
}
