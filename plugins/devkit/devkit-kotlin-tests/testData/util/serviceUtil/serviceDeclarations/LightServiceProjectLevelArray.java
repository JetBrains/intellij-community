package serviceDeclarations;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;


/**
 * PROJECT
 */
@Service({Service.Level.PROJECT})
public final class LightServiceProjectLevelArray {
  public static LightServiceProjectLevelArray getInstance(Project project) {
    return project.getService(LightServiceProjectLevelArray.class);
  }
}
