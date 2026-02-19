import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import kotlinx.coroutines.CoroutineScope;

@Service(Service.Level.PROJECT)
final class MyService {
  private final Project myProject;
  private final CoroutineScope myScope;

  public MyService(Project project, CoroutineScope scope) {
    myProject = project;
    myScope = scope;
  }
}
