import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.PROJECT)
final class MyService {

  public static MyService getInstance(Project project) {
    return project.getService(MyService.class);
  }

  void foo(Project project) {
    MyService service = project.<weak_warning descr="Can be replaced with 'MyService.getInstance()' call">get<caret>Service</weak_warning>(MyService.class);
  }
}