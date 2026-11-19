import com.intellij.openapi.workspace.ProjectWorkspace;

import serviceDeclarations.LightServiceWorkspaceLevel;

class MyClazz24 {
  void foo24(ProjectWorkspace workspace) {
    Object obj = <caret>LightServiceWorkspaceLevel.getInstance(workspace);
  }
}