import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service
final class MyService {

  public static MyService getInstance(Project project) {
    return project.getService(MyService.class);
  }

  void foo(Project project) {
    MyService service = <weak_warning descr="Can be replaced with 'MyService.getInstance()' call">project.getService<caret>(MyService.class)</weak_warning>;
  }
}