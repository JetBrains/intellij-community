import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@<warning descr="If constructor takes Project, Service.Level.PROJECT is required">Service<caret></warning>
final class MyService {
  private final Project myProject;

  public MyService(Project project) {
    myProject = project;
  }
}
