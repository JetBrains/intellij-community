import com.intellij.openapi.project.Project;

import serviceDeclarations.LightServiceProjectLevel;

class MyClazz19 {
  void foo19(Project project) {
    Object obj = <caret>LightServiceProjectLevel.getInstance(project);
  }
}