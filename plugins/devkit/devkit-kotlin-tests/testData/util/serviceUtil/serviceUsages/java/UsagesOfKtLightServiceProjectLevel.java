import com.intellij.openapi.project.Project;

import serviceDeclarations.KtLightServiceProjectLevel;

class MyClazz20 {
  void foo20(Project project) {
    Object obj = <caret>KtLightServiceProjectLevel.getInstance(project);
  }
}