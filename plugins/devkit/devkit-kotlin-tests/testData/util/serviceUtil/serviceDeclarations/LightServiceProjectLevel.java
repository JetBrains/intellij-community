package serviceDeclarations;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

/**
 * PROJECT
 */
@Service(Service.Level.PROJECT)

public final class LightServiceProjectLevel {
  public static LightServiceProjectLevel getInstance(Project project) {
    return project.getService(LightServiceProjectLevel.class);
  }
}
