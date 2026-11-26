package serviceDeclarations;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.workspace.Workspace;

/**
 * WORKSPACE
 */
@Service(Service.Level.WORKSPACE)
public final class LightServiceWorkspaceLevel {
  public static LightServiceWorkspaceLevel getInstance(Workspace workspace) {
    return workspace.getService(LightServiceWorkspaceLevel.class);
  }
}
