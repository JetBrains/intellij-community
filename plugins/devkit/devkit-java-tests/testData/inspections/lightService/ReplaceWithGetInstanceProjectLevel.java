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
    MyService service = <warning descr="Can be replaced with 'MyService.getInstance()' call">project.getService<caret>(MyService.class)</warning>;
  }
}