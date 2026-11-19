import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@<warning descr="Light service with a constructor that takes a parameter of type 'Project' must specify '@Service(Service.Level.PROJECT)'">Service</warning>(Service.Level.WORKSPACE)
final class MyService {
  private final Project myProject;

  public <warning descr="Workspace-level service requires a no-arg constructor or a constructor with 'ProjectWorkspace' and 'kotlinx.coroutines.CoroutineScope' parameters">MyService</warning>(Project project) {
    myProject = project;
  }
}
