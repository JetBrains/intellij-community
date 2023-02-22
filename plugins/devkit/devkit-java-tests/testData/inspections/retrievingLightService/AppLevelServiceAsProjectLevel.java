import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.APP)
final class MyService {
  void foo(Project project) {
    MyService service = <warning descr="The application-level service is retrieved as a project-level service">project.getService(MyService.class)</warning>;
  }
}
