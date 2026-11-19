package serviceDeclarations;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.workspace.ProjectWorkspace;

/**
 * WORKSPACE
 */
@Service(Service.Level.WORKSPACE)
public final class LightServiceWorkspaceLevel {
  public static LightServiceWorkspaceLevel getInstance(ProjectWorkspace workspace) {
    return workspace.getService(LightServiceWorkspaceLevel.class);
  }
}
