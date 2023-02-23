import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service
final class MyService {
  private final Project myProject;

  public <warning descr="If constructor takes Project, Service.Level.PROJECT is required">MyService<caret></warning>(Project project) {
    myProject = project;
  }
}
