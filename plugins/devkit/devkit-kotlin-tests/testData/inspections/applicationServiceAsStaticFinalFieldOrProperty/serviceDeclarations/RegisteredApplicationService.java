package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;

public class RegisteredApplicationService {
  public static RegisteredApplicationService getInstance() {
    return ApplicationManager.getApplication().getService(RegisteredApplicationService.class);
  }
}
