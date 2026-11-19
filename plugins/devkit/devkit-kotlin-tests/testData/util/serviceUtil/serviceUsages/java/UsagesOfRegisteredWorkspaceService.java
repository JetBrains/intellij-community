import com.intellij.openapi.workspace.ProjectWorkspace;

import serviceDeclarations.RegisteredWorkspaceService;

class MyClazz25 {
  void foo25(ProjectWorkspace workspace) {
    Object obj = <caret>RegisteredWorkspaceService.getInstance(workspace);
  }
}