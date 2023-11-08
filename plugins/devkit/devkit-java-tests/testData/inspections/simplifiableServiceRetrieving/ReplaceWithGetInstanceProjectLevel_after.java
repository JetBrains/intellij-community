import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.PROJECT)
final class MyService {

  public static MyService getInstance(Project project) {
    return project.getService(MyService.class);
  }

  void foo(Project project) {
    MyService service = MyService.getInstance(project);
  }
}