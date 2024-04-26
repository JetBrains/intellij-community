import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.Nullable;

@Service
final class MyAppService {
  public static @Nullable MyAppService getInstance() {
    return ApplicationManager.getApplication().getService(MyAppService.class);
  }

  void foo() {
    MyAppService service = ApplicationManager.getApplication().getService(MyAppService.class);
  }
}

@Service(Service.Level.PROJECT)
final class MyProjectService {
  public static @Nullable MyProjectService getInstance() {
    return ApplicationManager.getApplication().getService(MyProjectService.class);
  }

  void foo() {
    MyProjectService service = ApplicationManager.getApplication().getService(MyProjectService.class);
  }
}