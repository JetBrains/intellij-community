import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service
final class MyService {

  public static MyService getInstance(Project project) {
    return project.getService(MyService.class);
  }

  void foo(Project project) {
    System.out.println(42);
    MyService service = MyService.getInstance(project);
  }
}