import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@<warning descr="Light service with a constructor that takes a parameter of type 'Project' must specify '@Service(Service.Level.PROJECT)'">Service<caret></warning>(Service.Level.APP)
final class MyService {
  private final Project myProject;

  public <warning descr="Application-level service requires a no-arg or single parameter constructor with 'kotlinx.coroutines.CoroutineScope' type">MyService</warning>(Project project) {
    myProject = project;
  }
}
