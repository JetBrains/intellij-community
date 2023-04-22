import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
final class MyService {
  void foo(@NotNull Project project) {
    MyService service = <warning descr="The application-level service is retrieved as a project-level service">project.getService(MyService.class)</warning>;
  }
}
