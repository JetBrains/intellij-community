import com.intellij.openapi.project.Project;

import serviceDeclarations.LightServiceProjectLevelArray;

class MyClazz5 {
  void foo5(Project project) {
    Object obj = <caret>LightServiceProjectLevelArray.getInstance(project);
  }
}