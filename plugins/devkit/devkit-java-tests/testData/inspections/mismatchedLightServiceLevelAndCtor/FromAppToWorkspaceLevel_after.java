import com.intellij.openapi.components.Service;
import com.intellij.openapi.workspace.ProjectWorkspace;

@Service(Service.Level.WORKSPACE)
final class MyService {
  private final ProjectWorkspace myWorkspace;

  public MyService(ProjectWorkspace workspace) {
    myWorkspace = workspace;
  }
}
