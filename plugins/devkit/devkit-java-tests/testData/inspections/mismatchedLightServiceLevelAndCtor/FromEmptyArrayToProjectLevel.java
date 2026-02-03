import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@<warning descr="Light service with a constructor that takes a parameter of type 'Project' must specify '@Service(Service.Level.PROJECT)'">Service<caret></warning>({})
final class MyService {
  private final Project myProject;

  public MyService(Project project) {
    myProject = project;
  }
}
