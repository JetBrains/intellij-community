import com.intellij.openapi.workspace.Workspace;

import serviceDeclarations.RegisteredWorkspaceService;

class MyClazz25 {
  void foo25(Workspace workspace) {
    Object obj = <caret>RegisteredWorkspaceService.getInstance(workspace);
  }
}