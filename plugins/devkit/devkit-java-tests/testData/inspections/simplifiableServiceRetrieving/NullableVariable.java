import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
final class MyProjectService {
  public static MyProjectService getInstance(Project project) {
    return project.getService(MyProjectService.class);
  }

  void test(Project project) {
    @Nullable MyProjectService myProjectService = project.getService(MyProjectService.class);
  }
}

@Service
final class MyAppService {
  public static MyAppService getInstance() {
    return ApplicationManager.getApplication().getService(MyAppService.class);
  }

  void test() {
    @Nullable MyAppService myAppService = ApplicationManager.getApplication().getService(MyAppService.class);
  }
}

