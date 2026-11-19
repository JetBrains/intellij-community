package serviceDeclarations;

import com.intellij.openapi.workspace.ProjectWorkspace;

/**
 * WORKSPACE
 */
public class RegisteredWorkspaceService {
  public static RegisteredWorkspaceService getInstance(ProjectWorkspace workspace) {
    return workspace.getService(RegisteredWorkspaceService.class);
  }
}
