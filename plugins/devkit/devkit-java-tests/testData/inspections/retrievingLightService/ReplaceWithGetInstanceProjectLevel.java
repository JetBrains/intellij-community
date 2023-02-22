import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service
final class MyService {

  public static MyService getInstance(Project project) {
    return project.getService(MyService.class);
  }

  void foo(Project project) {
    System.out.println(42);
    MyService service = <warning descr="Can be replaced with 'MyService.getInstance()' call">project.getService<caret>(MyService.class)</warning>;
  }
}