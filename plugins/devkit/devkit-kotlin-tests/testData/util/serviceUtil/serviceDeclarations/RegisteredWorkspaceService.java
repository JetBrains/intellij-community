package serviceDeclarations;

import com.intellij.openapi.workspace.Workspace;

/**
 * WORKSPACE
 */
public class RegisteredWorkspaceService {
  public static RegisteredWorkspaceService getInstance(Workspace workspace) {
    return workspace.getService(RegisteredWorkspaceService.class);
  }
}
