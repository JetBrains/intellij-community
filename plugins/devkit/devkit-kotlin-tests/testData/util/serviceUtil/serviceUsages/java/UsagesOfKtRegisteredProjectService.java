import com.intellij.openapi.project.Project;

import serviceDeclarations.KtRegisteredProjectService;

class MyClazz18 {
  void foo18(Project project) {
    Object obj = <caret>KtRegisteredProjectService.getInstance(project);
  }
}