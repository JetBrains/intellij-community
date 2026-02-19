package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;

/**
 * MODULE
 */
public class RegisteredModuleService {
  public static RegisteredModuleService getInstance(Module module) {
    return module.getService(RegisteredModuleService.class);
  }
}
