import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Service({})
final class MyService {
  private @NotNull final Project myProject;

  public <warning descr="If constructor takes Project, Service.Level.PROJECT is required">MyService<caret></warning>(@NotNull Project project) {
    myProject = project;
  }
}
