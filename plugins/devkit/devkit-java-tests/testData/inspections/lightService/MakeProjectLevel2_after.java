import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
final class MyService {
  private @NotNull final Project myProject;

  public MyService(@NotNull Project project) {
    myProject = project;
  }
}
