import com.intellij.openapi.components.Service;
import com.intellij.openapi.workspace.Workspace;

@Service(Service.Level.WORKSPACE)
final class MyService {
  private final Workspace myWorkspace;

  public MyService(Workspace workspace) {
    myWorkspace = workspace;
  }
}
