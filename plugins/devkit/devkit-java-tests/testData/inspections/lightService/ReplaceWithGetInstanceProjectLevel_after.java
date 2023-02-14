import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Service
final class MyService {

  public static MyService getInstance(@NotNull Project project) {
    return project.getService(MyService.class);
  }

  void foo(@NotNull Project project) {
    System.out.println(42);
    MyService service = MyService.getInstance(project);
  }
}