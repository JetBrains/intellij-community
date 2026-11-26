import com.intellij.openapi.workspace.Workspace;

import serviceDeclarations.LightServiceWorkspaceLevel;

class MyClazz24 {
  void foo24(Workspace workspace) {
    Object obj = <caret>LightServiceWorkspaceLevel.getInstance(workspace);
  }
}