import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.PROJECT)
final class MyService {
  private final Project myProject;

  public MyService(Project project) {
    myProject = project;
  }
}
